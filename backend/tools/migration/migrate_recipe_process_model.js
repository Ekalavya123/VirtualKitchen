// Migration script for MongoDB (run with mongo or mongosh)
// Purpose: introduce the Recipe -> Process -> ProcessNode model (Phase 1) on
// top of existing dev/test data.
//
//   - Ensures every `process_template` ("Recipe") document has a
//     `mainProcessId`, by creating a new MAIN `processes` document from its
//     legacy `flows` graph document where one exists (joined the same way
//     RecipeTemplateServiceImpl's copyFlow() does:
//     flows.flowId == String(process_template._id)), or an empty MAIN
//     process otherwise, "where appropriate" per template.
//   - Backfills each migrated node's `kind` (STEP/PROCESS/CONDITION) from
//     its legacy React-Flow `type` string. Any node whose type isn't
//     "recipeStepNode"/"conditionNode" (e.g. parallel/section nodes, or a
//     missing type) is defaulted to STEP so no node/data is silently
//     dropped, and is logged so it can be reviewed manually -- the new
//     model has no PARALLEL/SECTION kind yet.
//   - `ingredients` defaulting to [] and `nutrition` defaulting to null on
//     `process_template` need NO migration: Spring Data MongoDB leaves a
//     Java field's default value (the entity's own field initializer)
//     untouched for any key absent from the stored document, so old
//     process_template documents already behave as `ingredients: []` /
//     `nutrition: null` without being rewritten. `mainProcessId` is the one
//     field that genuinely can't default itself -- it has to be produced by
//     actually creating a Process document, hence this script.
//   - Does not touch the legacy process_template_step / step_definition /
//     process_execution / step_execution collections -- nothing in the new
//     model reads them, and this codebase currently has no production data,
//     so there is nothing to backfill there.
//
// Safe to re-run: only process_template documents still missing
// mainProcessId are processed, so a second run is a no-op.
//
// Usage (mongo shell):
//   mongosh "mongodb+srv://user:pass@host/dbname" ./migrate_recipe_process_model.js
// or
//   mongo <connectionString> migrate_recipe_process_model.js

var STEP_TYPES = ['recipeStepNode'];
var CONDITION_TYPES = ['conditionNode'];

var processesCreated = 0;
var linkedToExistingFlow = 0;
var fallbackNodeTypes = [];

function nextProcessId() {
  var counter = db.database_sequences.findOneAndUpdate(
    {_id: 'process_sequence'},
    {$inc: {seq: 1}},
    {upsert: true, returnDocument: 'after'}
  );
  return counter.seq;
}

function toKind(nodeType, templateId, nodeId) {
  if (STEP_TYPES.indexOf(nodeType) !== -1) {
    return 'STEP';
  }
  if (CONDITION_TYPES.indexOf(nodeType) !== -1) {
    return 'CONDITION';
  }
  fallbackNodeTypes.push({templateId: templateId, nodeId: nodeId, legacyType: nodeType});
  return 'STEP';
}

function toProcessNode(node, templateId) {
  return {
    id: node.id,
    kind: toKind(node.type, templateId, node.id),
    data: node.data || {},
    processId: null,
    type: node.type,
    position: node.position,
    measured: node.measured,
    width: node.width,
    height: node.height,
    parentId: node.parentId,
    extent: node.extent,
    draggable: node.draggable,
    selectable: node.selectable,
    deletable: node.deletable
  };
}

function toProcessEdge(edge) {
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    sourceHandle: edge.sourceHandle,
    targetHandle: edge.targetHandle,
    type: edge.type,
    animated: edge.animated,
    style: edge.style,
    data: edge.data || {},
    label: edge.label
  };
}

db.process_template.find({mainProcessId: {$exists: false}}).forEach(function (template) {
  var flow = db.flows.findOne({flowId: String(template._id)});
  var now = new Date();

  var processDoc = {
    _id: nextProcessId(),
    type: 'MAIN',
    recipeId: template._id,
    name: template.name,
    description: template.description,
    nodes: [],
    edges: [],
    viewport: null,
    createdAt: now,
    updatedAt: now
  };

  if (flow) {
    processDoc.nodes = (flow.nodes || []).map(function (n) { return toProcessNode(n, template._id); });
    processDoc.edges = (flow.edges || []).map(toProcessEdge);
    processDoc.viewport = flow.viewport || null;
    linkedToExistingFlow++;
    print('MIGRATED flow ' + flow.flowId + ' -> process ' + processDoc._id + ' for recipe ' + template._id
      + ' (' + processDoc.nodes.length + ' node(s), ' + processDoc.edges.length + ' edge(s))');
  } else {
    print('CREATED empty MAIN process ' + processDoc._id + ' for recipe ' + template._id + ' (no existing flow document)');
  }

  db.processes.insertOne(processDoc);
  processesCreated++;

  db.process_template.updateOne({_id: template._id}, {$set: {mainProcessId: processDoc._id}});
});

print('Migration finished. Processes created: ' + processesCreated + ', linked to an existing flow: ' + linkedToExistingFlow + '.');

if (fallbackNodeTypes.length > 0) {
  print('WARNING: ' + fallbackNodeTypes.length + ' node(s) had an unrecognized/legacy type and were defaulted to STEP kind. Review before relying on their kind:');
  print(tojson(fallbackNodeTypes));
}

// Note: the legacy `flows` collection is intentionally left in place (not deleted) so the
// existing /api/v1/flows endpoints and frontend flow editor keep working unchanged until a
// later phase migrates them onto the new Process API.
