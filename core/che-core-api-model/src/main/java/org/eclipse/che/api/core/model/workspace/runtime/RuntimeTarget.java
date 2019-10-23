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
package org.eclipse.che.api.core.model.workspace.runtime;

import java.util.Objects;
import org.eclipse.che.commons.annotation.Nullable;

public final class RuntimeTarget {
  private final RuntimeIdentity identity;
  private final String infrastructureNamespace;
  private final String ownerName;

  public RuntimeTarget(RuntimeIdentity identity, @Nullable String ownerName,
          @Nullable String infrastructureNamespace) {
    this.identity = identity;
    this.ownerName = ownerName;
    this.infrastructureNamespace = infrastructureNamespace;
  }

  public RuntimeIdentity getIdentity() {
    return identity;
  }

  @Nullable
  public String getOwnerName() {
    return ownerName;
  }

  @Nullable
  public String getInfrastructureNamespace() {
    return infrastructureNamespace;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RuntimeTarget that = (RuntimeTarget) o;
    return identity.equals(that.identity) &&
            Objects.equals(infrastructureNamespace, that.infrastructureNamespace) &&
            Objects.equals(ownerName, that.ownerName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(identity, infrastructureNamespace, ownerName);
  }
}
