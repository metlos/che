/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.workspace.server;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.eclipse.che.api.workspace.shared.Constants.ERROR_MESSAGE_ATTRIBUTE_NAME;
import static org.eclipse.che.api.workspace.shared.Constants.NO_ENVIRONMENT_RECIPE_TYPE;
import static org.eclipse.che.api.workspace.shared.Constants.STOPPED_ABNORMALLY_ATTRIBUTE_NAME;
import static org.eclipse.che.api.workspace.shared.Constants.STOPPED_ATTRIBUTE_NAME;
import static org.eclipse.che.api.workspace.shared.Constants.WORKSPACE_INFRASTRUCTURE_NAMESPACE_ATTRIBUTE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.che.account.spi.AccountImpl;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.ValidationException;
import org.eclipse.che.api.core.model.workspace.Runtime;
import org.eclipse.che.api.core.model.workspace.Warning;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.core.model.workspace.config.Command;
import org.eclipse.che.api.core.model.workspace.config.Environment;
import org.eclipse.che.api.core.model.workspace.runtime.Machine;
import org.eclipse.che.api.core.model.workspace.runtime.MachineStatus;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.devfile.convert.DevfileConverter;
import org.eclipse.che.api.workspace.server.event.RuntimeAbnormalStoppedEvent;
import org.eclipse.che.api.workspace.server.event.RuntimeAbnormalStoppingEvent;
import org.eclipse.che.api.workspace.server.hc.probe.ProbeScheduler;
import org.eclipse.che.api.workspace.server.model.impl.CommandImpl;
import org.eclipse.che.api.workspace.server.model.impl.EnvironmentImpl;
import org.eclipse.che.api.workspace.server.model.impl.MachineImpl;
import org.eclipse.che.api.workspace.server.model.impl.RecipeImpl;
import org.eclipse.che.api.workspace.server.model.impl.RuntimeIdentityImpl;
import org.eclipse.che.api.workspace.server.model.impl.RuntimeImpl;
import org.eclipse.che.api.workspace.server.model.impl.RuntimeTarget;
import org.eclipse.che.api.workspace.server.model.impl.WarningImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.model.impl.devfile.DevfileImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalRuntime;
import org.eclipse.che.api.workspace.server.spi.RuntimeContext;
import org.eclipse.che.api.workspace.server.spi.RuntimeInfrastructure;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;
import org.eclipse.che.api.workspace.server.spi.environment.InternalEnvironment;
import org.eclipse.che.api.workspace.server.spi.environment.InternalEnvironmentFactory;
import org.eclipse.che.api.workspace.shared.dto.RuntimeIdentityDto;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.core.db.DBInitializer;
import org.eclipse.che.dto.server.DtoFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** Tests {@link WorkspaceRuntimes}. */
@Listeners(MockitoTestNGListener.class)
public class WorkspaceRuntimesTest {

  private static final String TEST_ENVIRONMENT_TYPE = "test";

  @Mock private EventService eventService;

  @Mock private WorkspaceDao workspaceDao;

  @Mock private DBInitializer dbInitializer;

  @Mock private WorkspaceSharedPool sharedPool;

  @Mock private ProbeScheduler probeScheduler;

  @Mock private WorkspaceLockService lockService;

  @Mock private WorkspaceStatusCache statuses;

  @Mock private DevfileConverter devfileConverter;

  private RuntimeInfrastructure infrastructure;

  @Mock private InternalEnvironmentFactory<InternalEnvironment> testEnvFactory;

  @Mock private UserManager userManager;

  private WorkspaceRuntimes runtimes;

  @BeforeMethod
  public void setUp() throws Exception {
    infrastructure = spy(new TestInfrastructure());

    runtimes =
        new WorkspaceRuntimes(
            eventService,
            ImmutableMap.of(TEST_ENVIRONMENT_TYPE, testEnvFactory),
            infrastructure,
            sharedPool,
            workspaceDao,
            dbInitializer,
            probeScheduler,
            statuses,
            lockService,
            devfileConverter,
            userManager);
  }

  @Test(
      expectedExceptions = NotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Workspace 'account:ws' doesn't contain environment 'non-existing'")
  public void throwsNotFoundExceptionWhenStartWorkspaceWithNotExistingEnv() throws Exception {
    final WorkspaceImpl workspace = new WorkspaceImpl();
    WorkspaceConfigImpl config = new WorkspaceConfigImpl();
    workspace.setAccount(new AccountImpl("acc123", "account", "any"));
    workspace.setConfig(config);
    config.setName("ws");
    config.getEnvironments().put("default", new EnvironmentImpl());

    runtimes.validate(workspace, "non-existing");
  }

  @Test
  public void internalEnvironmentCreationShouldRespectNoEnvironmentCase() throws Exception {
    InternalEnvironmentFactory noEnvFactory = mock(InternalEnvironmentFactory.class);
    runtimes =
        new WorkspaceRuntimes(
            eventService,
            ImmutableMap.of(
                TEST_ENVIRONMENT_TYPE, testEnvFactory, NO_ENVIRONMENT_RECIPE_TYPE, noEnvFactory),
            infrastructure,
            sharedPool,
            workspaceDao,
            dbInitializer,
            probeScheduler,
            statuses,
            lockService,
            devfileConverter,
            userManager);
    InternalEnvironment expectedEnvironment = mock(InternalEnvironment.class);
    when(noEnvFactory.create(eq(null))).thenReturn(expectedEnvironment);

    InternalEnvironment actualEnvironment =
        runtimes.createInternalEnvironment(null, emptyMap(), emptyList());

    assertEquals(actualEnvironment, expectedEnvironment);
  }

  @Test(
      expectedExceptions = NotFoundException.class,
      expectedExceptionsMessageRegExp =
          "InternalEnvironmentFactory is not configured for recipe type: 'not-supported-type'")
  public void internalEnvironmentShouldThrowExceptionWhenNoEnvironmentFactoryFoundForRecipeType()
      throws Exception {
    EnvironmentImpl environment = new EnvironmentImpl();
    environment.setRecipe(new RecipeImpl("not-supported-type", "", "", null));
    runtimes.createInternalEnvironment(environment, emptyMap(), emptyList());
  }

  @Test(
      expectedExceptions = NotFoundException.class,
      expectedExceptionsMessageRegExp =
          "InternalEnvironmentFactory is not configured for recipe type: '"
              + NO_ENVIRONMENT_RECIPE_TYPE
              + "'")
  public void
      internalEnvironmentShouldThrowExceptionWhenNoEnvironmentFactoryFoundForNoEnvironmentWorkspaceCase()
          throws Exception {
    runtimes.createInternalEnvironment(null, emptyMap(), emptyList());
  }

  @Test
  public void runtimeIsRecoveredForWorkspaceWithConfig() throws Exception {
    RuntimeIdentity identity = new RuntimeIdentityImpl("workspace123", "my-env", "myId");
    RuntimeTarget target = new RuntimeTarget(identity, null, "nmspc");
    mockWorkspaceWithConfig(target);
    RuntimeContext context = mockContext(target);
    when(context.getRuntime())
        .thenReturn(new TestInternalRuntime(context, emptyMap(), WorkspaceStatus.STARTING));
    doReturn(context).when(infrastructure).prepare(eq(target), any());
    doReturn(mock(InternalEnvironment.class)).when(testEnvFactory).create(any());
    when(statuses.get(anyString())).thenReturn(WorkspaceStatus.STARTING);

    // try recover
    runtimes.recoverOne(infrastructure, identity);

    WorkspaceImpl workspace = WorkspaceImpl.builder().setId(identity.getWorkspaceId()).build();
    runtimes.injectRuntime(workspace);
    assertNotNull(workspace.getRuntime());
    assertEquals(workspace.getStatus(), WorkspaceStatus.STARTING);
  }

  @Test
  public void runtimeIsRecoveredForWorkspaceWithDevfile() throws Exception {
    RuntimeIdentity identity = new RuntimeIdentityImpl("workspace123", "default", "myId");
    RuntimeTarget target = new RuntimeTarget(identity, null, "nmspc");

    WorkspaceImpl workspaceMock = mockWorkspaceWithDevfile(target);
    RuntimeContext context = mockContext(target);
    when(context.getRuntime())
        .thenReturn(new TestInternalRuntime(context, emptyMap(), WorkspaceStatus.STARTING));
    doReturn(context).when(infrastructure).prepare(eq(target), any());
    doReturn(mock(InternalEnvironment.class)).when(testEnvFactory).create(any());
    when(statuses.get(anyString())).thenReturn(WorkspaceStatus.STARTING);

    // try recover
    runtimes.recoverOne(infrastructure, identity);

    WorkspaceImpl workspace = WorkspaceImpl.builder().setId(identity.getWorkspaceId()).build();
    runtimes.injectRuntime(workspace);
    assertNotNull(workspace.getRuntime());
    assertEquals(workspace.getStatus(), WorkspaceStatus.STARTING);

    verify(devfileConverter).convert(workspaceMock.getDevfile());
  }

  @Test(
      expectedExceptions = ServerException.class,
      expectedExceptionsMessageRegExp =
          "Workspace configuration is missing for the runtime 'workspace123:my-env'. Runtime won't be recovered")
  public void runtimeIsNotRecoveredIfNoWorkspaceFound() throws Exception {
    RuntimeIdentity identity = new RuntimeIdentityImpl("workspace123", "my-env", "myId");
    when(workspaceDao.get(identity.getWorkspaceId())).thenThrow(new NotFoundException("no!"));

    // try recover
    runtimes.recoverOne(infrastructure, identity);

    assertFalse(runtimes.hasRuntime(identity.getWorkspaceId()));
  }

  @Test(
      expectedExceptions = ServerException.class,
      expectedExceptionsMessageRegExp =
          "Environment configuration is missing for the runtime 'workspace123:my-env'. Runtime won't be recovered")
  public void runtimeIsNotRecoveredIfNoEnvironmentFound() throws Exception {
    RuntimeIdentity identity = new RuntimeIdentityImpl("workspace123", "my-env", "myId");
    RuntimeTarget target = new RuntimeTarget(identity, null, "nmspc");
    WorkspaceImpl workspace = mockWorkspaceWithConfig(target);
    when(workspace.getConfig().getEnvironments()).thenReturn(emptyMap());

    // try recover
    runtimes.recoverOne(infrastructure, identity);

    assertFalse(runtimes.hasRuntime(identity.getWorkspaceId()));
  }

  @Test(
      expectedExceptions = ServerException.class,
      expectedExceptionsMessageRegExp =
          "Couldn't recover runtime 'workspace123:my-env'. Error: oops!")
  public void runtimeIsNotRecoveredIfInfraPreparationFailed() throws Exception {
    RuntimeIdentity identity = new RuntimeIdentityImpl("workspace123", "my-env", "myId");
    RuntimeTarget target = new RuntimeTarget(identity, null, "nmspc");

    mockWorkspaceWithConfig(target);
    InternalEnvironment internalEnvironment = mock(InternalEnvironment.class);
    doReturn(internalEnvironment).when(testEnvFactory).create(any(Environment.class));
    doThrow(new InfrastructureException("oops!"))
        .when(infrastructure)
        .prepare(eq(target), any(InternalEnvironment.class));

    // try recover
    runtimes.recoverOne(infrastructure, identity);

    assertFalse(runtimes.hasRuntime(identity.getWorkspaceId()));
  }

  @Test
  public void runtimeRecoveryContinuesThroughException() throws Exception {
    // Given
    RuntimeIdentityImpl identity1 = new RuntimeIdentityImpl("workspace1", "env1", "owner1");
    RuntimeIdentityImpl identity2 = new RuntimeIdentityImpl("workspace2", "env2", "owner2");
    RuntimeIdentityImpl identity3 = new RuntimeIdentityImpl("workspace3", "env3", "owner3");
    RuntimeTarget target1 = new RuntimeTarget(identity1, null, "nmspc");
    RuntimeTarget target2 = new RuntimeTarget(identity2, null, "nmspc");
    RuntimeTarget target3 = new RuntimeTarget(identity3, null, "nmspc");

    Set<RuntimeIdentity> identities =
        ImmutableSet.<RuntimeIdentity>builder()
            .add(identity1)
            .add(identity2)
            .add(identity3)
            .build();
    doReturn(identities).when(infrastructure).getIdentities();

    mockWorkspaceWithConfig(target1);
    mockWorkspaceWithConfig(target2);
    mockWorkspaceWithConfig(target3);
    when(statuses.get(anyString())).thenReturn(WorkspaceStatus.STARTING);

    RuntimeContext context1 = mockContext(target1);
    when(context1.getRuntime())
        .thenReturn(new TestInternalRuntime(context1, emptyMap(), WorkspaceStatus.STARTING));
    doReturn(context1).when(infrastructure).prepare(eq(target1), any());
    RuntimeContext context2 = mockContext(target1);
    when(context2.getRuntime())
        .thenReturn(new TestInternalRuntime(context2, emptyMap(), WorkspaceStatus.STARTING));
    doReturn(context2).when(infrastructure).prepare(eq(target2), any());
    RuntimeContext context3 = mockContext(target1);
    when(context3.getRuntime())
        .thenReturn(new TestInternalRuntime(context3, emptyMap(), WorkspaceStatus.STARTING));
    doReturn(context3).when(infrastructure).prepare(eq(target3), any());

    InternalEnvironment internalEnvironment = mock(InternalEnvironment.class);
    doReturn(internalEnvironment).when(testEnvFactory).create(any(Environment.class));

    // Want to fail recovery of identity2
    doThrow(new InfrastructureException("oops!"))
        .when(infrastructure)
        .prepare(eq(target2), any(InternalEnvironment.class));

    // When
    runtimes.new RecoverRuntimesTask(identities).run();

    // Then
    verify(infrastructure).prepare(target1, internalEnvironment);
    verify(infrastructure).prepare(target2, internalEnvironment);
    verify(infrastructure).prepare(target3, internalEnvironment);

    WorkspaceImpl workspace1 = WorkspaceImpl.builder().setId(identity1.getWorkspaceId()).build();
    runtimes.injectRuntime(workspace1);
    assertNotNull(workspace1.getRuntime());
    assertEquals(workspace1.getStatus(), WorkspaceStatus.STARTING);
    WorkspaceImpl workspace3 = WorkspaceImpl.builder().setId(identity3.getWorkspaceId()).build();
    runtimes.injectRuntime(workspace3);
    assertNotNull(workspace3.getRuntime());
    assertEquals(workspace3.getStatus(), WorkspaceStatus.STARTING);
  }

  @Test
  public void runtimeRecoveryContinuesThroughRuntimeException() throws Exception {
    // Given
    RuntimeIdentityImpl identity1 = new RuntimeIdentityImpl("workspace1", "env1", "owner1");
    RuntimeIdentityImpl identity2 = new RuntimeIdentityImpl("workspace2", "env2", "owner2");
    RuntimeIdentityImpl identity3 = new RuntimeIdentityImpl("workspace3", "env3", "owner3");
    RuntimeTarget target1 = new RuntimeTarget(identity1, null, "nmspc");
    RuntimeTarget target2 = new RuntimeTarget(identity2, null, "nmspc");
    RuntimeTarget target3 = new RuntimeTarget(identity3, null, "nmspc");
    Set<RuntimeIdentity> identities =
        ImmutableSet.<RuntimeIdentity>builder()
            .add(identity1)
            .add(identity2)
            .add(identity3)
            .build();
    doReturn(identities).when(infrastructure).getIdentities();

    mockWorkspaceWithConfig(target1);
    mockWorkspaceWithConfig(target2);
    mockWorkspaceWithConfig(target3);
    when(statuses.get(anyString())).thenReturn(WorkspaceStatus.STARTING);

    RuntimeContext context1 = mockContext(target1);
    when(context1.getRuntime())
        .thenReturn(new TestInternalRuntime(context1, emptyMap(), WorkspaceStatus.STARTING));
    doReturn(context1).when(infrastructure).prepare(eq(target1), any());
    RuntimeContext context2 = mockContext(target1);
    when(context2.getRuntime())
        .thenReturn(new TestInternalRuntime(context2, emptyMap(), WorkspaceStatus.STARTING));
    doReturn(context2).when(infrastructure).prepare(eq(target2), any());
    RuntimeContext context3 = mockContext(target1);
    when(context3.getRuntime())
        .thenReturn(new TestInternalRuntime(context3, emptyMap(), WorkspaceStatus.STARTING));
    doReturn(context3).when(infrastructure).prepare(eq(target3), any());

    InternalEnvironment internalEnvironment = mock(InternalEnvironment.class);
    doReturn(internalEnvironment).when(testEnvFactory).create(any(Environment.class));

    // Want to fail recovery of target2
    doThrow(new RuntimeException("oops!"))
        .when(infrastructure)
        .prepare(eq(target2), any(InternalEnvironment.class));

    // When
    runtimes.new RecoverRuntimesTask(identities).run();

    // Then
    verify(infrastructure).prepare(target1, internalEnvironment);
    verify(infrastructure).prepare(target2, internalEnvironment);
    verify(infrastructure).prepare(target3, internalEnvironment);

    WorkspaceImpl workspace1 = WorkspaceImpl.builder().setId(identity1.getWorkspaceId()).build();
    runtimes.injectRuntime(workspace1);
    assertNotNull(workspace1.getRuntime());
    assertEquals(workspace1.getStatus(), WorkspaceStatus.STARTING);
    WorkspaceImpl workspace3 = WorkspaceImpl.builder().setId(identity3.getWorkspaceId()).build();
    runtimes.injectRuntime(workspace3);
    assertNotNull(workspace3.getRuntime());
    assertEquals(workspace3.getStatus(), WorkspaceStatus.STARTING);
  }

  @Test
  public void attributesIsSetWhenRuntimeAbnormallyStopped() throws Exception {
    String error = "Some kind of error happened";
    EventService localEventService = new EventService();
    WorkspaceRuntimes localRuntimes =
        new WorkspaceRuntimes(
            localEventService,
            ImmutableMap.of(TEST_ENVIRONMENT_TYPE, testEnvFactory),
            infrastructure,
            sharedPool,
            workspaceDao,
            dbInitializer,
            probeScheduler,
            statuses,
            lockService,
            devfileConverter,
            userManager);
    localRuntimes.init();
    RuntimeIdentityDto identity =
        DtoFactory.newDto(RuntimeIdentityDto.class)
            .withWorkspaceId("workspace123")
            .withEnvName("my-env")
            .withOwnerId("myId");
    RuntimeTarget target = new RuntimeTarget(identity, null, "nmspc");

    mockWorkspaceWithConfig(target);
    RuntimeContext context = mockContext(target);
    when(context.getRuntime()).thenReturn(new TestInternalRuntime(context));
    when(statuses.remove(anyString())).thenReturn(WorkspaceStatus.RUNNING);

    RuntimeAbnormalStoppedEvent event = new RuntimeAbnormalStoppedEvent(identity, error);
    localRuntimes.recoverOne(infrastructure, identity);
    ArgumentCaptor<WorkspaceImpl> captor = ArgumentCaptor.forClass(WorkspaceImpl.class);

    // when
    localEventService.publish(event);

    // then
    verify(workspaceDao, atLeastOnce()).update(captor.capture());
    WorkspaceImpl ws = captor.getAllValues().get(captor.getAllValues().size() - 1);
    assertNotNull(ws.getAttributes().get(STOPPED_ATTRIBUTE_NAME));
    assertTrue(Boolean.valueOf(ws.getAttributes().get(STOPPED_ABNORMALLY_ATTRIBUTE_NAME)));
    assertEquals(ws.getAttributes().get(ERROR_MESSAGE_ATTRIBUTE_NAME), error);
  }

  @Test
  public void stoppingStatusIsSetWhenRuntimeAbnormallyStopping() throws Exception {
    String error = "Some kind of error happened";
    EventService localEventService = new EventService();
    WorkspaceRuntimes localRuntimes =
        new WorkspaceRuntimes(
            localEventService,
            ImmutableMap.of(TEST_ENVIRONMENT_TYPE, testEnvFactory),
            infrastructure,
            sharedPool,
            workspaceDao,
            dbInitializer,
            probeScheduler,
            statuses,
            lockService,
            devfileConverter,
            userManager);
    localRuntimes.init();
    RuntimeIdentityDto identity =
        DtoFactory.newDto(RuntimeIdentityDto.class)
            .withWorkspaceId("workspace123")
            .withEnvName("my-env")
            .withOwnerId("myId");
    RuntimeTarget target = new RuntimeTarget(identity, null, "nmspc");

    mockWorkspaceWithConfig(target);
    RuntimeContext context = mockContext(target);
    when(context.getRuntime()).thenReturn(new TestInternalRuntime(context));

    RuntimeAbnormalStoppingEvent event = new RuntimeAbnormalStoppingEvent(identity, error);
    localRuntimes.recoverOne(infrastructure, identity);

    // when
    localEventService.publish(event);

    // then
    verify(statuses).replace("workspace123", WorkspaceStatus.STOPPING);
  }

  @Test
  public void shouldInjectRuntime() throws Exception {
    // given
    WorkspaceImpl workspace = new WorkspaceImpl();
    workspace.setId("ws123");
    when(statuses.get("ws123")).thenReturn(WorkspaceStatus.RUNNING);

    ImmutableMap<String, Machine> machines =
        ImmutableMap.of("machine", new MachineImpl(emptyMap(), emptyMap(), MachineStatus.STARTING));

    RuntimeIdentity identity = new RuntimeIdentityImpl("ws123", "my-env", "myId");
    RuntimeTarget target = new RuntimeTarget(identity, null, "nmspc");
    RuntimeContext context = mockContext(target);
    doReturn(context).when(infrastructure).prepare(eq(target), any());

    ConcurrentHashMap<String, InternalRuntime<?>> runtimesStorage = new ConcurrentHashMap<>();
    TestInternalRuntime testRuntime =
        new TestInternalRuntime(context, machines, WorkspaceStatus.STARTING);
    runtimesStorage.put("ws123", testRuntime);
    WorkspaceRuntimes localRuntimes =
        new WorkspaceRuntimes(
            runtimesStorage,
            eventService,
            ImmutableMap.of(TEST_ENVIRONMENT_TYPE, testEnvFactory),
            infrastructure,
            sharedPool,
            workspaceDao,
            dbInitializer,
            probeScheduler,
            statuses,
            lockService,
            devfileConverter,
            userManager);

    // when
    localRuntimes.injectRuntime(workspace);

    // then
    assertEquals(workspace.getStatus(), WorkspaceStatus.RUNNING);
    assertEquals(workspace.getRuntime(), asRuntime(testRuntime));
  }

  @Test
  public void shouldRecoverRuntimeWhenThereIsNotCachedOneDuringInjecting() throws Exception {
    // given
    RuntimeIdentity identity = new RuntimeIdentityImpl("workspace123", "my-env", "myId");
    RuntimeTarget target = new RuntimeTarget(identity, null, "nmspc");
    mockWorkspaceWithConfig(target);

    when(statuses.get("workspace123")).thenReturn(WorkspaceStatus.STARTING);
    RuntimeContext context = mockContext(target);
    doReturn(context).when(infrastructure).prepare(eq(target), any());
    ImmutableMap<String, Machine> machines =
        ImmutableMap.of("machine", new MachineImpl(emptyMap(), emptyMap(), MachineStatus.STARTING));
    TestInternalRuntime testRuntime =
        new TestInternalRuntime(context, machines, WorkspaceStatus.STARTING);
    when(context.getRuntime()).thenReturn(testRuntime);
    doReturn(mock(InternalEnvironment.class)).when(testEnvFactory).create(any());
    doReturn(ImmutableSet.of(identity)).when(infrastructure).getIdentities();

    // when
    WorkspaceImpl workspace = new WorkspaceImpl();
    workspace.setId("workspace123");
    runtimes.injectRuntime(workspace);

    // then
    assertEquals(workspace.getStatus(), WorkspaceStatus.STARTING);
    assertEquals(workspace.getRuntime(), asRuntime(testRuntime));
  }

  @Test
  public void shouldNotInjectRuntimeIfThereIsNoCachedStatus() throws Exception {
    // when
    WorkspaceImpl workspace = new WorkspaceImpl();
    workspace.setId("workspace123");
    runtimes.injectRuntime(workspace);

    // then
    assertEquals(workspace.getStatus(), WorkspaceStatus.STOPPED);
    assertNull(workspace.getRuntime());
  }

  @Test
  public void shouldNotInjectRuntimeIfExceptionOccurredOnRuntimeFetching() throws Exception {
    // given
    RuntimeIdentity identity = new RuntimeIdentityImpl("workspace123", "my-env", "myId");
    RuntimeTarget target = new RuntimeTarget(identity, null, "nmspc");
    mockWorkspaceWithConfig(target);

    when(statuses.get("workspace123")).thenReturn(WorkspaceStatus.STARTING);
    RuntimeContext context = mockContext(target);
    ImmutableMap<String, Machine> machines =
        ImmutableMap.of("machine", new MachineImpl(emptyMap(), emptyMap(), MachineStatus.STARTING));
    when(context.getRuntime())
        .thenReturn(new TestInternalRuntime(context, machines, WorkspaceStatus.STARTING));
    doThrow(new InfrastructureException("error")).when(infrastructure).prepare(eq(target), any());

    // when
    WorkspaceImpl workspace = new WorkspaceImpl();
    workspace.setId("workspace123");
    runtimes.injectRuntime(workspace);

    // then
    assertEquals(workspace.getStatus(), WorkspaceStatus.STOPPED);
    assertNull(workspace.getRuntime());
  }

  @Test
  public void shouldReturnWorkspaceStatus() {
    // given
    when(statuses.get("ws123")).thenReturn(WorkspaceStatus.STOPPING);

    // when
    WorkspaceStatus fetchedStatus = runtimes.getStatus("ws123");

    // then
    assertEquals(fetchedStatus, WorkspaceStatus.STOPPING);
  }

  @Test
  public void shouldReturnStoppedWorkspaceStatusIfThereIsNotCachedValue() {
    // given
    when(statuses.get("ws123")).thenReturn(null);

    // when
    WorkspaceStatus fetchedStatus = runtimes.getStatus("ws123");

    // then
    assertEquals(fetchedStatus, WorkspaceStatus.STOPPED);
  }

  @Test
  public void shouldReturnTrueIfThereIsCachedRuntimeStatusOnRuntimeExistenceChecking() {
    // given
    when(statuses.get("ws123")).thenReturn(WorkspaceStatus.STOPPING);

    // when
    boolean hasRuntime = runtimes.hasRuntime("ws123");

    // then
    assertTrue(hasRuntime);
  }

  @Test
  public void shouldReturnFalseIfThereIsNoCachedRuntimeStatusOnRuntimeExistenceChecking() {
    // given
    when(statuses.get("ws123")).thenReturn(null);

    // when
    boolean hasRuntime = runtimes.hasRuntime("ws123");

    // then
    assertFalse(hasRuntime);
  }

  @Test
  public void shouldReturnRuntimesIdsOfActiveWorkspaces() {
    // given
    when(statuses.asMap())
        .thenReturn(
            ImmutableMap.of(
                "ws1", WorkspaceStatus.STARTING,
                "ws2", WorkspaceStatus.RUNNING,
                "ws3", WorkspaceStatus.STOPPING));

    // when
    Set<String> active = runtimes.getActive();

    // then
    assertEquals(active.size(), 3);
    assertTrue(active.containsAll(asList("ws1", "ws2", "ws3")));
  }

  @Test
  public void shouldReturnWorkspaceIdsOfRunningRuntimes() {
    // given
    when(statuses.asMap())
        .thenReturn(
            ImmutableMap.of(
                "ws1", WorkspaceStatus.STARTING,
                "ws2", WorkspaceStatus.RUNNING,
                "ws3", WorkspaceStatus.RUNNING,
                "ws4", WorkspaceStatus.RUNNING,
                "ws5", WorkspaceStatus.STOPPING));

    // when
    Set<String> running = runtimes.getRunning();

    // then
    assertEquals(running.size(), 3);
    assertTrue(running.containsAll(asList("ws2", "ws3", "ws4")));
  }

  private RuntimeContext mockContext(RuntimeTarget target)
      throws ValidationException, InfrastructureException {
    RuntimeIdentity identity = target.getIdentity();
    RuntimeContext context = mock(RuntimeContext.class);
    InternalEnvironment internalEnvironment = mock(InternalEnvironment.class);
    lenient().doReturn(internalEnvironment).when(testEnvFactory).create(any(Environment.class));
    lenient().doReturn(context).when(infrastructure).prepare(eq(target), eq(internalEnvironment));
    lenient().when(context.getInfrastructure()).thenReturn(infrastructure);
    lenient().when(context.getIdentity()).thenReturn(identity);
    lenient().when(context.getTarget()).thenReturn(target);
    lenient().when(context.getEnvironment()).thenReturn(internalEnvironment);

    List<Warning> warnings = new ArrayList<>();
    warnings.add(createWarning());
    lenient().when(internalEnvironment.getWarnings()).thenReturn(warnings);

    return context;
  }

  private WorkspaceImpl mockWorkspaceWithConfig(RuntimeTarget target)
      throws NotFoundException, ServerException {
    RuntimeIdentity identity = target.getIdentity();
    WorkspaceConfigImpl config = mock(WorkspaceConfigImpl.class);
    EnvironmentImpl environment = mockEnvironment();
    when(config.getEnvironments()).thenReturn(ImmutableMap.of(identity.getEnvName(), environment));

    WorkspaceImpl workspace = mock(WorkspaceImpl.class);
    when(workspace.getConfig()).thenReturn(config);
    when(workspace.getId()).thenReturn(identity.getWorkspaceId());
    when(workspace.getAttributes())
        .thenReturn(
            singletonMap(
                WORKSPACE_INFRASTRUCTURE_NAMESPACE_ATTRIBUTE, target.getInfrastructureNamespace()));

    lenient().when(workspaceDao.get(identity.getWorkspaceId())).thenReturn(workspace);

    return workspace;
  }

  private WorkspaceImpl mockWorkspaceWithDevfile(RuntimeTarget target)
      throws NotFoundException, ServerException {

    RuntimeIdentity identity = target.getIdentity();

    DevfileImpl devfile = mock(DevfileImpl.class);

    WorkspaceImpl workspace = mock(WorkspaceImpl.class);
    lenient().when(workspace.getDevfile()).thenReturn(devfile);
    lenient().when(workspace.getId()).thenReturn(identity.getWorkspaceId());
    lenient()
        .when(workspace.getAttributes())
        .thenReturn(
            singletonMap(
                WORKSPACE_INFRASTRUCTURE_NAMESPACE_ATTRIBUTE, target.getInfrastructureNamespace()));

    lenient().when(workspaceDao.get(identity.getWorkspaceId())).thenReturn(workspace);

    WorkspaceConfigImpl convertedConfig = mock(WorkspaceConfigImpl.class);
    EnvironmentImpl environment = mockEnvironment();
    lenient()
        .when(convertedConfig.getEnvironments())
        .thenReturn(ImmutableMap.of(identity.getEnvName(), environment));
    lenient().when(devfileConverter.convert(devfile)).thenReturn(convertedConfig);

    return workspace;
  }

  private EnvironmentImpl mockEnvironment() {
    EnvironmentImpl environment = mock(EnvironmentImpl.class);
    when(environment.getRecipe())
        .thenReturn(new RecipeImpl(TEST_ENVIRONMENT_TYPE, "contentType1", "content1", null));
    return environment;
  }

  private Runtime asRuntime(TestInternalRuntime internalRuntime) throws InfrastructureException {
    return new RuntimeImpl(
        internalRuntime.getActiveEnv(),
        internalRuntime.getMachines(),
        internalRuntime.getOwner(),
        internalRuntime.getCommands(),
        internalRuntime.getWarnings());
  }

  private static class TestInfrastructure extends RuntimeInfrastructure {

    public TestInfrastructure() {
      this("test");
    }

    public TestInfrastructure(String... types) {
      super("test", asList(types), null, emptySet());
    }

    @Override
    public RuntimeContext internalPrepare(RuntimeTarget target, InternalEnvironment environment) {
      throw new UnsupportedOperationException();
    }
  }

  private static class TestInternalRuntime extends InternalRuntime<RuntimeContext> {

    final Map<String, Machine> machines;
    final List<? extends Command> commands;

    TestInternalRuntime(
        RuntimeContext context,
        Map<String, Machine> machines,
        List<? extends Command> commands,
        WorkspaceStatus status) {
      super(context, null, status);
      this.commands = commands;
      this.machines = machines;
    }

    TestInternalRuntime(
        RuntimeContext context, Map<String, Machine> machines, WorkspaceStatus status) {
      this(context, machines, singletonList(createCommand()), status);
    }

    TestInternalRuntime(RuntimeContext context, Map<String, Machine> machines) {
      this(context, machines, WorkspaceStatus.STARTING);
    }

    TestInternalRuntime(RuntimeContext context) {
      this(context, emptyMap());
    }

    @Override
    protected Map<String, Machine> getInternalMachines() {
      return machines;
    }

    @Override
    public List<? extends Command> getCommands() throws InfrastructureException {
      return commands;
    }

    @Override
    public Map<String, String> getProperties() {
      return emptyMap();
    }

    @Override
    protected void internalStop(Map stopOptions) throws InfrastructureException {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void internalStart(Map startOptions) throws InfrastructureException {
      throw new UnsupportedOperationException();
    }
  }

  private static CommandImpl createCommand() {
    return new CommandImpl(NameGenerator.generate("command-", 5), "echo Hello", "custom");
  }

  private static WarningImpl createWarning() {
    return new WarningImpl(123, "configuration parameter `123` is ignored");
  }
}
