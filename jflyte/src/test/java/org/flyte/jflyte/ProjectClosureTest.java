/*
 * Copyright 2020 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.flyte.jflyte;

import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flyte.api.v1.LaunchPlan;
import org.flyte.api.v1.LaunchPlanIdentifier;
import org.flyte.api.v1.Node;
import org.flyte.api.v1.PartialWorkflowIdentifier;
import org.flyte.api.v1.Struct;
import org.flyte.api.v1.TypedInterface;
import org.flyte.api.v1.WorkflowIdentifier;
import org.flyte.api.v1.WorkflowMetadata;
import org.flyte.api.v1.WorkflowNode;
import org.flyte.api.v1.WorkflowTemplate;
import org.junit.jupiter.api.Test;

public class ProjectClosureTest {

  @Test
  public void testMerge() {
    Struct source =
        Struct.of(
            ImmutableMap.of(
                "a", Struct.Value.ofStringValue("a0"),
                "b", Struct.Value.ofStringValue("b0")));

    Struct target =
        Struct.of(
            ImmutableMap.of(
                "b", Struct.Value.ofStringValue("b1"),
                "c", Struct.Value.ofStringValue("c1")));

    Struct expected =
        Struct.of(
            ImmutableMap.of(
                "a", Struct.Value.ofStringValue("a0"),
                "b", Struct.Value.ofStringValue("b0"),
                "c", Struct.Value.ofStringValue("c1")));

    assertThat(expected.fields().size(), equalTo(3));

    assertThat(ProjectClosure.merge(source, target), equalTo(expected));
  }

  @Test
  public void testCollectSubWorkflows() {
    TypedInterface emptyInterface =
        TypedInterface.builder().inputs(ImmutableMap.of()).outputs(ImmutableMap.of()).build();

    WorkflowMetadata emptyMetadata = WorkflowMetadata.builder().build();

    WorkflowTemplate emptyWorkflowTemplate =
        WorkflowTemplate.builder()
            .interface_(emptyInterface)
            .metadata(emptyMetadata)
            .nodes(ImmutableList.of())
            .outputs(ImmutableList.of())
            .build();

    PartialWorkflowIdentifier rewrittenSubWorkflowRef =
        PartialWorkflowIdentifier.builder()
            .project("project")
            .name("name")
            .version("version")
            .domain("domain")
            .build();

    WorkflowIdentifier subWorkflowRef =
        WorkflowIdentifier.builder()
            .project("project")
            .name("name")
            .version("version")
            .domain("domain")
            .build();

    WorkflowIdentifier otherSubWorkflowRef =
        WorkflowIdentifier.builder()
            .project("project")
            .name("other-name")
            .version("version")
            .domain("domain")
            .build();

    WorkflowNode workflowNode =
        WorkflowNode.builder()
            .reference(WorkflowNode.Reference.ofSubWorkflowRef(rewrittenSubWorkflowRef))
            .build();

    List<Node> nodes =
        ImmutableList.of(
            Node.builder()
                .id("node-1")
                .inputs(ImmutableList.of())
                .upstreamNodeIds(ImmutableList.of())
                .workflowNode(workflowNode)
                .build(),
            // Same sub-workflow
            Node.builder()
                .id("node-2")
                .inputs(ImmutableList.of())
                .upstreamNodeIds(ImmutableList.of())
                .workflowNode(workflowNode)
                .build());

    Map<WorkflowIdentifier, WorkflowTemplate> allWorkflows =
        ImmutableMap.of(
            subWorkflowRef, emptyWorkflowTemplate,
            otherSubWorkflowRef, emptyWorkflowTemplate);

    Map<WorkflowIdentifier, WorkflowTemplate> collectedSubWorkflows =
        ProjectClosure.collectSubWorkflows(nodes, allWorkflows);

    assertThat(
        collectedSubWorkflows, equalTo(ImmutableMap.of(subWorkflowRef, emptyWorkflowTemplate)));
  }

  @Test
  public void testSerialize() {
    Map<String, ByteString> output = new HashMap<>();

    LaunchPlanIdentifier id0 =
        LaunchPlanIdentifier.builder()
            .domain("placeholder")
            .project("placeholder")
            .name("name0")
            .version("placeholder")
            .build();

    LaunchPlanIdentifier id1 =
        LaunchPlanIdentifier.builder()
            .domain("placeholder")
            .project("placeholder")
            .name("name1")
            .version("placeholder")
            .build();

    LaunchPlan launchPlan =
        LaunchPlan.builder()
            .name("name")
            .workflowId(
                PartialWorkflowIdentifier.builder()
                    .name("name")
                    .project("placeholder")
                    .domain("placeholder")
                    .version("placeholder")
                    .build())
            .build();

    ProjectClosure closure =
        ProjectClosure.builder()
            .workflowSpecs(emptyMap())
            .taskSpecs(emptyMap())
            .launchPlans(
                ImmutableMap.of(
                    id0, launchPlan,
                    id1, launchPlan))
            .build();

    closure.serialize(output::put);

    assertThat(output.keySet(), containsInAnyOrder("0_name0_3.pb", "1_name1_3.pb"));
  }
}