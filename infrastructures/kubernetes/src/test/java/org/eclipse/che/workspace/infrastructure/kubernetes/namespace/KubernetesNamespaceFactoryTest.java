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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace;

import static java.util.Collections.singletonList;
import static org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta.DEFAULT_ATTRIBUTE;
import static org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta.PHASE_ATTRIBUTE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.DoneableNamespace;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.Arrays;
import java.util.List;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.inject.ConfigurationException;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests {@link KubernetesNamespaceFactory}.
 *
 * @author Sergii Leshchenko
 */
@Listeners(MockitoTestNGListener.class)
public class KubernetesNamespaceFactoryTest {
  @Mock private KubernetesClientFactory clientFactory;

  @Mock private KubernetesClient k8sClient;

  private KubernetesNamespaceFactory namespaceFactory;

  @Test(
      expectedExceptions = ConfigurationException.class,
      expectedExceptionsMessageRegExp =
          "che.infra.kubernetes.namespace.default or "
              + "che.infra.kubernetes.namespace.allow_user_defined must be configured")
  public void
      shouldThrowExceptionIfNoDefaultNamespaceIsConfiguredAndUserDefinedNamespacesAreNotAllowed()
          throws Exception {
    namespaceFactory =
        new KubernetesNamespaceFactory("predefined", "", "", null, false, clientFactory);
  }

  @Test
  public void shouldReturnDefaultNamespaceOnlyWhenItExistsAndUserDefinedIsNotAllowed()
      throws Exception {
    prepareNamespaceToBeFoundByName(
        "che-default",
        new NamespaceBuilder()
            .withNewMetadata()
            .withName("che-default")
            .endMetadata()
            .withNewStatus("Active")
            .build());
    namespaceFactory =
        new KubernetesNamespaceFactory("predefined", "", "", "che-default", false, clientFactory);

    List<KubernetesNamespaceMeta> availableNamespaces = namespaceFactory.list();
    assertEquals(availableNamespaces.size(), 1);
    KubernetesNamespaceMeta defaultNamespace = availableNamespaces.get(0);
    assertEquals(defaultNamespace.getName(), "che-default");
    assertEquals(defaultNamespace.getAttributes().get(DEFAULT_ATTRIBUTE), "true");
    assertEquals(defaultNamespace.getAttributes().get(PHASE_ATTRIBUTE), "Active");
  }

  @Test
  public void shouldReturnDefaultNamespaceOnlyWhenItDoesNotExistAndUserDefinedIsNotAllowed()
      throws Exception {
    prepareNamespaceToBeFoundByName("che-default", null);

    namespaceFactory =
        new KubernetesNamespaceFactory("predefined", "", "", "che-default", false, clientFactory);

    List<KubernetesNamespaceMeta> availableNamespaces = namespaceFactory.list();
    assertEquals(availableNamespaces.size(), 1);
    KubernetesNamespaceMeta defaultNamespace = availableNamespaces.get(0);
    assertEquals(defaultNamespace.getName(), "che-default");
    assertEquals(defaultNamespace.getAttributes().get(DEFAULT_ATTRIBUTE), "true");
    assertNull(
        defaultNamespace
            .getAttributes()
            .get(PHASE_ATTRIBUTE)); // no phase - means such namespace does not exist
  }

  @Test
  public void shouldReturnListOfExistingNamespacesIfUserDefinedIsAllowed() throws Exception {
    prepareListedNamespaces(
        Arrays.asList(
            createNamespace("my-for-ws", "Active"),
            createNamespace("experimental", "Terminating")));

    namespaceFactory =
        new KubernetesNamespaceFactory("predefined", "", "", null, true, clientFactory);

    List<KubernetesNamespaceMeta> availableNamespaces = namespaceFactory.list();
    assertEquals(availableNamespaces.size(), 2);
    KubernetesNamespaceMeta forWS = availableNamespaces.get(0);
    assertEquals(forWS.getName(), "my-for-ws");
    assertEquals(forWS.getAttributes().get(PHASE_ATTRIBUTE), "Active");

    KubernetesNamespaceMeta experimental = availableNamespaces.get(1);
    assertEquals(experimental.getName(), "experimental");
    assertEquals(experimental.getAttributes().get(PHASE_ATTRIBUTE), "Terminating");
  }

  @Test
  public void shouldReturnListOfExistingNamespacesAlongWithDefaultIfUserDefinedIsAllowed()
      throws Exception {
    prepareListedNamespaces(
        Arrays.asList(
            createNamespace("my-for-ws", "Active"), createNamespace("default", "Active")));

    namespaceFactory =
        new KubernetesNamespaceFactory("predefined", "", "", "default", true, clientFactory);

    List<KubernetesNamespaceMeta> availableNamespaces = namespaceFactory.list();

    assertEquals(availableNamespaces.size(), 2);
    KubernetesNamespaceMeta forWS = availableNamespaces.get(0);
    assertEquals(forWS.getName(), "my-for-ws");
    assertEquals(forWS.getAttributes().get(PHASE_ATTRIBUTE), "Active");
    assertNull(forWS.getAttributes().get(DEFAULT_ATTRIBUTE));

    KubernetesNamespaceMeta defaultNamespace = availableNamespaces.get(1);
    assertEquals(defaultNamespace.getName(), "default");
    assertEquals(defaultNamespace.getAttributes().get(PHASE_ATTRIBUTE), "Active");
    assertEquals(defaultNamespace.getAttributes().get(DEFAULT_ATTRIBUTE), "true");
  }

  @Test
  public void
      shouldReturnListOfExistingNamespacesAlongWithNonExistingDefaultIfUserDefinedIsAllowed()
          throws Exception {
    prepareListedNamespaces(singletonList(createNamespace("my-for-ws", "Active")));

    namespaceFactory =
        new KubernetesNamespaceFactory("predefined", "", "", "default", true, clientFactory);

    List<KubernetesNamespaceMeta> availableNamespaces = namespaceFactory.list();
    assertEquals(availableNamespaces.size(), 2);
    KubernetesNamespaceMeta forWS = availableNamespaces.get(0);
    assertEquals(forWS.getName(), "my-for-ws");
    assertEquals(forWS.getAttributes().get(PHASE_ATTRIBUTE), "Active");
    assertNull(forWS.getAttributes().get(DEFAULT_ATTRIBUTE));

    KubernetesNamespaceMeta defaultNamespace = availableNamespaces.get(1);
    assertEquals(defaultNamespace.getName(), "default");
    assertEquals(defaultNamespace.getAttributes().get(DEFAULT_ATTRIBUTE), "true");
    assertNull(
        defaultNamespace
            .getAttributes()
            .get(PHASE_ATTRIBUTE)); // no phase - means such namespace does not exist
  }

  @Test
  public void shouldReturnTrueIfNamespaceIsNotEmptyOnCheckingIfNamespaceIsPredefined() {
    // given
    namespaceFactory =
        new KubernetesNamespaceFactory("predefined", "", "", "che", false, clientFactory);

    // when
    boolean isPredefined = namespaceFactory.isPredefined();

    // then
    assertTrue(isPredefined);
  }

  @Test
  public void shouldReturnTrueIfNamespaceIsEmptyOnCheckingIfNamespaceIsPredefined() {
    // given
    namespaceFactory = new KubernetesNamespaceFactory("", "", "", "che", false, clientFactory);

    // when
    boolean isPredefined = namespaceFactory.isPredefined();

    // then
    assertFalse(isPredefined);
  }

  @Test
  public void shouldReturnTrueIfNamespaceIsNullOnCheckingIfNamespaceIsPredefined() {
    // given
    namespaceFactory = new KubernetesNamespaceFactory(null, "", "", "che", false, clientFactory);

    // when
    boolean isPredefined = namespaceFactory.isPredefined();

    // then
    assertFalse(isPredefined);
  }

  @Test
  public void shouldCreateAndPrepareNamespaceWithPredefinedValueIfItIsNotEmpty() throws Exception {
    // given
    namespaceFactory =
        spy(new KubernetesNamespaceFactory("predefined", "", "", "che", false, clientFactory));
    KubernetesNamespace toReturnNamespace = mock(KubernetesNamespace.class);
    doReturn(toReturnNamespace).when(namespaceFactory).doCreateNamespace(any(), any());

    // when
    KubernetesNamespace namespace = namespaceFactory.create("workspace123");

    // then
    assertEquals(toReturnNamespace, namespace);
    verify(namespaceFactory).doCreateNamespace("workspace123", "predefined");
    verify(toReturnNamespace).prepare();
  }

  @Test
  public void shouldCreateAndPrepareNamespaceWithWorkspaceIdAsNameIfConfiguredNameIsNotPredefined()
      throws Exception {
    // given
    namespaceFactory = spy(new KubernetesNamespaceFactory("", "", "", "che", false, clientFactory));
    KubernetesNamespace toReturnNamespace = mock(KubernetesNamespace.class);
    doReturn(toReturnNamespace).when(namespaceFactory).doCreateNamespace(any(), any());

    // when
    KubernetesNamespace namespace = namespaceFactory.create("workspace123");

    // then
    assertEquals(toReturnNamespace, namespace);
    verify(namespaceFactory).doCreateNamespace("workspace123", "workspace123");
    verify(toReturnNamespace).prepare();
  }

  @Test
  public void
      shouldCreateNamespaceAndDoNotPrepareNamespaceOnCreatingNamespaceWithWorkspaceIdAndNameSpecified()
          throws Exception {
    // given
    namespaceFactory = spy(new KubernetesNamespaceFactory("", "", "", "che", false, clientFactory));
    KubernetesNamespace toReturnNamespace = mock(KubernetesNamespace.class);
    doReturn(toReturnNamespace).when(namespaceFactory).doCreateNamespace(any(), any());

    // when
    KubernetesNamespace namespace = namespaceFactory.create("workspace123", "name");

    // then
    assertEquals(toReturnNamespace, namespace);
    verify(namespaceFactory).doCreateNamespace("workspace123", "name");
    verify(toReturnNamespace, never()).prepare();
  }

  @Test
  public void shouldPrepareWorkspaceServiceAccountIfItIsConfiguredAndNamespaceIsNotPredefined()
      throws Exception {
    // given
    namespaceFactory =
        spy(new KubernetesNamespaceFactory("", "serviceAccount", "", "che", false, clientFactory));
    KubernetesNamespace toReturnNamespace = mock(KubernetesNamespace.class);
    doReturn(toReturnNamespace).when(namespaceFactory).doCreateNamespace(any(), any());

    KubernetesWorkspaceServiceAccount serviceAccount =
        mock(KubernetesWorkspaceServiceAccount.class);
    doReturn(serviceAccount).when(namespaceFactory).doCreateServiceAccount(any(), any());

    // when
    namespaceFactory.create("workspace123");

    // then
    verify(namespaceFactory).doCreateServiceAccount("workspace123", "workspace123");
    verify(serviceAccount).prepare();
  }

  @Test
  public void shouldNotPrepareWorkspaceServiceAccountIfItIsConfiguredAndProjectIsPredefined()
      throws Exception {
    // given
    namespaceFactory =
        spy(
            new KubernetesNamespaceFactory(
                "namespace", "serviceAccount", "clusterRole", "che", false, clientFactory));
    KubernetesNamespace toReturnNamespace = mock(KubernetesNamespace.class);
    doReturn(toReturnNamespace).when(namespaceFactory).doCreateNamespace(any(), any());

    KubernetesWorkspaceServiceAccount serviceAccount =
        mock(KubernetesWorkspaceServiceAccount.class);
    doReturn(serviceAccount).when(namespaceFactory).doCreateServiceAccount(any(), any());

    // when
    namespaceFactory.create("workspace123");

    // then
    verify(namespaceFactory, never()).doCreateServiceAccount(any(), any());
  }

  @Test
  public void shouldNotPrepareWorkspaceServiceAccountIfItIsNotConfiguredAndProjectIsNotPredefined()
      throws Exception {
    // given
    namespaceFactory = spy(new KubernetesNamespaceFactory("", "", "", "che", false, clientFactory));
    KubernetesNamespace toReturnNamespace = mock(KubernetesNamespace.class);
    doReturn(toReturnNamespace).when(namespaceFactory).doCreateNamespace(any(), any());

    KubernetesWorkspaceServiceAccount serviceAccount =
        mock(KubernetesWorkspaceServiceAccount.class);
    doReturn(serviceAccount).when(namespaceFactory).doCreateServiceAccount(any(), any());

    // when
    namespaceFactory.create("workspace123");

    // then
    verify(namespaceFactory, never()).doCreateServiceAccount(any(), any());
  }

  @Test
  public void testPlaceholder() {
    namespaceFactory =
        new KubernetesNamespaceFactory(
            "blabol-<userid>-<username>-<userid>-<username>--",
            "",
            "",
            "che",
            false,
            clientFactory);
    String namespace =
        namespaceFactory.evalNamespaceName(null, new SubjectImpl("JonDoe", "123", null, false));

    assertEquals(namespace, "blabol-123-JonDoe-123-JonDoe--");
  }

  private void prepareListedNamespaces(List<Namespace> namespaces) throws Exception {
    when(clientFactory.create()).thenReturn(k8sClient);

    @SuppressWarnings("unchecked")
    NonNamespaceOperation<
            Namespace, NamespaceList, DoneableNamespace, Resource<Namespace, DoneableNamespace>>
        namespaceOperation = mock(NonNamespaceOperation.class);
    when(k8sClient.namespaces()).thenReturn(namespaceOperation);

    @SuppressWarnings("unchecked")
    NamespaceList namespaceListOp = mock(NamespaceList.class);
    when(namespaceOperation.list()).thenReturn(namespaceListOp);

    when(namespaceListOp.getItems()).thenReturn(namespaces);
  }

  private void prepareNamespaceToBeFoundByName(String name, Namespace namespace) throws Exception {
    when(clientFactory.create()).thenReturn(k8sClient);

    @SuppressWarnings("unchecked")
    NonNamespaceOperation<
            Namespace, NamespaceList, DoneableNamespace, Resource<Namespace, DoneableNamespace>>
        namespaceOperation = mock(NonNamespaceOperation.class);
    when(k8sClient.namespaces()).thenReturn(namespaceOperation);

    @SuppressWarnings("unchecked")
    Resource<Namespace, DoneableNamespace> getNamespaceByNameOperation = mock(Resource.class);
    when(namespaceOperation.withName(name)).thenReturn(getNamespaceByNameOperation);

    when(getNamespaceByNameOperation.get()).thenReturn(namespace);
  }

  private Namespace createNamespace(String name, String phase) {
    return new NamespaceBuilder()
        .withNewMetadata()
        .withName(name)
        .endMetadata()
        .withNewStatus(phase)
        .build();
  }
}
