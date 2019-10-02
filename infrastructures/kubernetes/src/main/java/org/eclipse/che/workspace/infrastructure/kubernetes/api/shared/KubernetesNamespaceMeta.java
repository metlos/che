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
package org.eclipse.che.workspace.infrastructure.kubernetes.api.shared;

import org.eclipse.che.api.core.model.workspace.InfrastructureNamespaceMetadata;

/**
 * Describes meta information about kubernetes namespace.
 *
 * @author Sergii Leshchenko
 */
public interface KubernetesNamespaceMeta extends InfrastructureNamespaceMetadata {

  /**
   * Attribute that shows if k8s namespace is configured as default. Possible values: true/false.
   * Absent value should be considered as false.
   */
  String DEFAULT_ATTRIBUTE = "default";

  /**
   * Attributes that contains information about current namespace status. Example values: Active,
   * Terminating. Absent value indicates that namespace is not created yet.
   */
  String PHASE_ATTRIBUTE = "phase";
}
