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
package org.eclipse.che.api.workspace.server.model.impl.devfile;

import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;
import org.eclipse.che.api.core.model.workspace.devfile.Metadata;

@Embeddable
public class MetadataImpl implements Metadata {

  @Column(name = "meta_name")
  private String name;

  @Column(name = "namespace")
  private String namespace;

  /**
   * generateName is used just at workspace create time, when name is generated from it and stored
   * into {@link MetadataImpl#name}, thus it's not needed to persist
   */
  @Transient private String generateName;

  public MetadataImpl() {}

  public MetadataImpl(String name) {
    this.name = name;
  }

  public MetadataImpl(Metadata metadata) {
    this.name = metadata.getName();
    this.generateName = metadata.getGenerateName();
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getGenerateName() {
    return generateName;
  }

  public void setGenerateName(String generateName) {
    this.generateName = generateName;
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MetadataImpl metadata = (MetadataImpl) o;
    return Objects.equals(name, metadata.name)
        && Objects.equals(generateName, metadata.generateName)
        && Objects.equals(namespace, metadata.namespace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, generateName, namespace);
  }

  @Override
  public String toString() {
    return "MetadataImpl{"
        + "name='"
        + name
        + '\''
        + ", generateName='"
        + generateName
        + '\''
        + ", namespace='"
        + namespace
        + '\''
        + '}';
  }
}
