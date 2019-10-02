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
package org.eclipse.che.api.workspace.server.spi;

import java.util.List;
import org.eclipse.che.api.core.model.workspace.InfrastructureNamespaceMetadata;

/**
 * A generic interface to obtain infrastructure-specific information about the infrastructure
 * namespaces available to the workspaces.
 */
public interface InfrastructureNamespaceService {

  /**
   * Lists the infrastructure namespaces available for deploying workspaces to. Note that the list
   * might contain both existing namespaces as well as entries representing namespace templates.
   */
  List<InfrastructureNamespaceMetadata> getAvailableNamespaces() throws InfrastructureException;

  /**
   * When creating a workspace, the {@link org.eclipse.che.api.workspace.server.WorkspaceManager}
   * needs to decide what infrastructure namespace this workspace will eventually be started in.
   * This method is used to propose the name that conforms to the configuration of the
   * infrastructure and, if possible, honors the provided {@code userDefinedName}.
   *
   * @param userDefinedName the name of the infrastructure namespace the user wants the workspace to
   *     be started in
   * @return the infrastructure namespace the workspace will be started in. Might or might not be
   *     equal to the provided namespace name.
   */
  String proposeName(String userDefinedName) throws InfrastructureException;
}
