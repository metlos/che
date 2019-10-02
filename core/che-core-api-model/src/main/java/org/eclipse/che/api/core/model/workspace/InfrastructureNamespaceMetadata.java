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
package org.eclipse.che.api.core.model.workspace;

import java.util.Map;
import java.util.regex.Pattern;

public interface InfrastructureNamespaceMetadata {

  /**
   * True if the template name contains placeholders and therefore doesn't represent any actual
   * infrastructure namespace, false otherwise.
   */
  default boolean isTemplate() {
    return TemplateCheckHolder.IS_TEMPLATE.matcher(getName()).find();
  }

  /**
   * Returns the name of namespace.
   *
   * <p>Value may be not a name of existing namespace, but predicted name with placeholders inside,
   * like <username>. For such names, the {@link #isTemplate()} method will return true.
   */
  String getName();

  /** Returns namespace attributes, which may contains additional info about it like description. */
  Map<String, String> getAttributes();
}

class TemplateCheckHolder {
  static final Pattern IS_TEMPLATE = Pattern.compile("<.+>");
}
