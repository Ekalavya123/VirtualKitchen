// Migration script for MongoDB (run with mongo or mongosh)
// Purpose: populate `kitchenId` field on `inventory` documents from `kitchen_inventory` join collection.
// Usage (mongo shell):
//   mongosh "mongodb+srv://user:pass@host/dbname" ./migrate_set_kitchenId_from_kitchen_inventory.js
// or
//   mongo <connectionString> migrate_set_kitchenId_from_kitchen_inventory.js

var conflicts = [];

db.kitchen_inventory.find().forEach(function(ki) {
  var inv = db.inventory.findOne({_id: ki.inventoryId});
  if (!inv) {
    print('MISSING inventory _id=' + ki.inventoryId + ' referenced by kitchen_inventory _id=' + ki._id);
    return;
  }

  if (inv.kitchenId === undefined || inv.kitchenId === null) {
    var res = db.inventory.updateOne({_id: inv._id}, {$set: {kitchenId: ki.kitchenId}});
    if (res.modifiedCount) {
      print('SET kitchenId=' + ki.kitchenId + ' on inventory _id=' + inv._id);
    } else {
      print('NO-OP updating inventory _id=' + inv._id);
    }
  } else if (inv.kitchenId !== ki.kitchenId) {
    // conflict: same inventory linked to multiple kitchens
    print('CONFLICT inventory _id=' + inv._id + ' has kitchenId=' + inv.kitchenId + ' but kitchen_inventory links to ' + ki.kitchenId);
    conflicts.push({inventoryId: inv._id, inventoryKitchen: inv.kitchenId, joinKitchen: ki.kitchenId, kitchenInventoryId: ki._id});
  }
});

print('Migration finished. Conflicts: ' + tojson(conflicts));

// Note: This script currently assigns kitchenId to the first matching kitchen_inventory entry
// for inventory documents that lack it. If an inventoryId is referenced by multiple kitchens,
// you will see conflicts printed and collected in the `conflicts` array. Decide how to
// resolve conflicts (duplicate inventory documents per kitchen, keep as-is, or migrate to a
// many-to-many model) before removing the `kitchen_inventory` collection.

