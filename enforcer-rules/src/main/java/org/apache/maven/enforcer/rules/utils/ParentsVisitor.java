/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.enforcer.rules.utils;

import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.maven.api.Node;
import org.apache.maven.api.NodeVisitor;

/**
 * A {@link NodeVisitor} building a map of parent nodes
 */
public class ParentsVisitor implements NodeVisitor, ParentNodeProvider {

    private final Map<Node, Node> parents = new HashMap<>();
    private final Deque<Node> parentStack = new ConcurrentLinkedDeque<>();

    @Override
    public Node getParent(Node node) {
        return parents.get(node);
    }

    @Override
    public boolean enter(Node node) {
        parents.put(node, parentStack.isEmpty() ? null : parentStack.peek());
        parentStack.push(node);
        return true;
    }

    @Override
    public boolean leave(Node node) {
        parentStack.pop();
        return true;
    }
}
