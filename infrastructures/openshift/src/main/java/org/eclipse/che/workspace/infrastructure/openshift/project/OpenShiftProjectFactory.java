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
package org.eclipse.che.workspace.infrastructure.openshift.project;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Collections.singletonList;
import static org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta.DEFAULT_ATTRIBUTE;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Project;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.server.impls.KubernetesNamespaceMetaImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.eclipse.che.workspace.infrastructure.openshift.Constants;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientConfigFactory;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helps to create {@link OpenShiftProject} instances.
 *
 * @author Anton Korneta
 */
@Singleton
public class OpenShiftProjectFactory extends KubernetesNamespaceFactory {
  private static final Logger LOG = LoggerFactory.getLogger(OpenShiftProjectFactory.class);

  private final OpenShiftClientFactory clientFactory;
  private final String defaultNamespaceName;
  private final boolean allowUserDefinedNamespaces;

  @Inject
  public OpenShiftProjectFactory(
      @Nullable @Named("che.infra.openshift.project") String projectName,
      @Nullable @Named("che.infra.kubernetes.service_account_name") String serviceAccountName,
      @Nullable @Named("che.infra.kubernetes.cluster_role_name") String clusterRoleName,
      @Nullable @Named("che.infra.kubernetes.namespace.default") String defaultNamespaceName,
      @Named("che.infra.kubernetes.namespace.allow_user_defined")
          boolean allowUserDefinedNamespaces,
      OpenShiftClientFactory clientFactory,
      OpenShiftClientConfigFactory clientConfigFactory) {
    super(
        projectName,
        serviceAccountName,
        clusterRoleName,
        defaultNamespaceName,
        allowUserDefinedNamespaces,
        clientFactory);
    if (allowUserDefinedNamespaces && !clientConfigFactory.isPersonalized()) {
      LOG.warn(
          "Users allowed to list projects but all reuse Che Server configured service account. "
              + "Consider configuring OpenShift OAuth to personalize credentials that will be used for cluster access.");
    }
    this.defaultNamespaceName = defaultNamespaceName;
    this.allowUserDefinedNamespaces = allowUserDefinedNamespaces;
    this.clientFactory = clientFactory;
  }

  @Override
  public List<KubernetesNamespaceMeta> list() throws InfrastructureException {
    if (!allowUserDefinedNamespaces) {
      // return only default project if user defined are not allowed
      String evaluatedName =
          evalDefaultNamespaceName(
              defaultNamespaceName, EnvironmentContext.getCurrent().getSubject());

      KubernetesNamespaceMeta defaultNamespace;
      try {
        Project project = clientFactory.createOC().projects().withName(evaluatedName).get();
        defaultNamespace = asNamespaceMeta(project);
      } catch (KubernetesClientException e) {
        if (e.getCode() == 403) {
          // 403 means that the project does not exist
          // or a user really is not permitted to access it which is Che Server misconfiguration
          //
          // return dummy info and Che Server will try to create such project during the first workspace start
          defaultNamespace = new KubernetesNamespaceMetaImpl(evaluatedName);
        } else {
          throw new InfrastructureException(e.getMessage(), e);
        }
      }

      defaultNamespace.getAttributes().put(DEFAULT_ATTRIBUTE, "true");
      return singletonList(defaultNamespace);
    }

    // if user defined namespaces are allowed - fetch all available
    List<KubernetesNamespaceMeta> projects =
        clientFactory
            .createOC()
            .projects()
            .list()
            .getItems()
            .stream()
            .map(this::asNamespaceMeta)
            .collect(Collectors.toList());

    // propagate default namespace if it's configured
    if (!isNullOrEmpty(defaultNamespaceName)) {
      String evaluatedName =
          evalDefaultNamespaceName(
              defaultNamespaceName, EnvironmentContext.getCurrent().getSubject());

      Optional<KubernetesNamespaceMeta> defaultNamespaceOpt =
          projects.stream().filter(n -> evaluatedName.equals(n.getName())).findAny();
      if (defaultNamespaceOpt.isPresent()) {
        defaultNamespaceOpt.get().getAttributes().put(DEFAULT_ATTRIBUTE, "true");
      } else {
        projects.add(new KubernetesNamespaceMetaImpl(evaluatedName));
      }
    }
    return projects;
  }

  /**
   * Creates a OpenShift project for the specified workspace.
   *
   * <p>The project name will be chosen according to a configuration, and it will be prepared
   * (created if necessary).
   *
   * @param workspaceId identifier of the workspace
   * @return created project
   * @throws InfrastructureException if any exception occurs during project preparing
   */
  public OpenShiftProject create(String workspaceId) throws InfrastructureException {
    final String projectName =
        evalNamespaceName(workspaceId, EnvironmentContext.getCurrent().getSubject());
    OpenShiftProject osProject = doCreateProject(workspaceId, projectName);
    osProject.prepare();

    if (!isPredefined() && !isNullOrEmpty(getServiceAccountName())) {
      // prepare service account for workspace only if account name is configured
      // and project is not predefined
      // since predefined project should be prepared during Che deployment
      OpenShiftWorkspaceServiceAccount osWorkspaceServiceAccount =
          doCreateServiceAccount(workspaceId, projectName);
      osWorkspaceServiceAccount.prepare();
    }

    return osProject;
  }

  /**
   * Creates a kubernetes namespace for the specified workspace.
   *
   * <p>Project won't be prepared. This method should be used only in case workspace recovering.
   *
   * @param workspaceId identifier of the workspace
   * @return created namespace
   */
  public OpenShiftProject create(String workspaceId, String projectName) {
    return doCreateProject(workspaceId, projectName);
  }

  @VisibleForTesting
  OpenShiftProject doCreateProject(String workspaceId, String name) {
    return new OpenShiftProject(clientFactory, name, workspaceId);
  }

  @VisibleForTesting
  OpenShiftWorkspaceServiceAccount doCreateServiceAccount(String workspaceId, String projectName) {
    return new OpenShiftWorkspaceServiceAccount(
        workspaceId, projectName, getServiceAccountName(), getClusterRoleName(), clientFactory);
  }

  private KubernetesNamespaceMeta asNamespaceMeta(io.fabric8.openshift.api.model.Project project) {
    Map<String, String> attributes = new HashMap<>();
    ObjectMeta metadata = project.getMetadata();
    Map<String, String> annotations = metadata.getAnnotations();
    String displayName = annotations.get(Constants.PROJECT_DISPLAY_NAME_ANNOTATION);
    if (displayName != null) {
      attributes.put(Constants.PROJECT_DISPLAY_NAME_ATTRIBUTE, displayName);
    }
    String description = annotations.get(Constants.PROJECT_DESCRIPTION_ANNOTATION);
    if (description != null) {
      attributes.put(Constants.PROJECT_DESCRIPTION_ATTRIBUTE, description);
    }

    return new KubernetesNamespaceMetaImpl(metadata.getName(), attributes);
  }
}
