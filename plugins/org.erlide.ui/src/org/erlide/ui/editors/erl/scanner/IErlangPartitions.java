/*******************************************************************************
 * Copyright (c) 2004 Vlad Dumitrescu and others. All rights reserved. This program and
 * the accompanying materials are made available under the terms of the Eclipse Public
 * License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.ui.editors.erl.scanner;

import org.eclipse.jface.text.IDocument;

public interface IErlangPartitions {

    String ERLANG_PARTITIONING = "___erlang_partitioning"; //$NON-NLS-1$

    String ERLANG_CHARACTER = "__erlang_character";
    String ERLANG_COMMENT = "__erlang_comment";
    String ERLANG_STRING = "__erlang_string";
    String ERLANG_QATOM = "__erlang_qatom";

    String[] LEGAL_PARTITIONS = {
            IDocument.DEFAULT_CONTENT_TYPE, IErlangPartitions.ERLANG_CHARACTER,
            IErlangPartitions.ERLANG_COMMENT, IErlangPartitions.ERLANG_QATOM,
            IErlangPartitions.ERLANG_STRING
    };

}
