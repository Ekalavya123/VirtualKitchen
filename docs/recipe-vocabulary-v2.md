# Recipe Process Vocabulary v2

This document proposes the expanded recipe step vocabulary (catalog schema v2), covering deliverables A–H. The single source of truth is
[`frontEnd/src/features/recipe-tool/catalog/stepCatalogs.data.json`](../frontEnd/src/features/recipe-tool/catalog/stepCatalogs.data.json).
The frontend imports it directly. The backend reads the same file from the classpath: `pom.xml` copies it there, and
`RecipeStepVocabularyProvider` loads it. That provider drives both the AI prompt vocabulary and the AI validator.
Neither side hardcodes catalog values.

## 0. Summary

| | POC (v1) | v2 |
|---|---|---|
| Actions | 20 in 7 categories | **117** in 15 categories |
| Ingredients | 19 | **420** in 27 categories, with aliases |
| Units | 10 (the Process step could only store 5: `COUNT/GRAM/KG/ML/LITER`) | **38** in 9 categories, all storable on a step |
| Preparation styles | 8, offered for every cutting action | **69** in 12 sets, filtered by action and ingredient category |
| Heat levels | low / medium / high | 9 levels, from `off` to `very-high` |
| Temperature | free text ("180 C") | number + `C`/`F`, plus a semantic context (oven / oil / liquid / surface / ambient) |
| Per-action rules | flat `fields` list, used only by the UI | a `fields` map with required / recommended / optional values, allowed Action On targets, and allowed preparation style sets. The UI and the AI validator both enforce them. |

**Backward compatibility.** Every v1 id is preserved: all 20 actions, 19 ingredients, 10 units, 8 styles and 4 flame levels. The
generator fails if one goes missing. Saved steps that stored the old `UnitType` enum (`COUNT`, `GRAM`, `KG`, `ML`, `LITER`) resolve
through unit aliases to `piece`, `g`, `kg`, `ml`, `l` when they are read. Saved free-text temperatures such as "180 C" are parsed into value + unit.
No database migration is needed. A step is rewritten in the new shape the next time it is saved.

### Design principles

1. **Action and Action On stay separate concepts.** An action declares *what it may act on* (`actionOn`). Separately, a step's
   Action On lists the concrete ingredients and subprocess references.
2. **Ingredient identity is separate from preparation state.** `onion` + `finely-chopped` is correct. `chopped-onion` does not exist.
   A few ingredients are distinct products that people buy and stock separately, so they get their own entries: cumin seeds vs. ground
   cumin, ground beef, ginger-garlic paste, stock, dough, canned tomatoes. A preparation state is never an ingredient.
3. **Fields are declared per action, not universal.** A field appears in the UI, and the AI may output it, only when the action declares it.
4. **Aliases are for input only.** They help search and help the AI read recipe text (e.g. *dhania*, *besan*, *aubergine*).
   Output and stored data always use the canonical id.
5. **Prefer specific over generic.** `cook` exists for recipes that name no technique. `custom` exists only as a last resort and
   always carries a name.
6. **Redundant verbs became aliases, not ids.** For example: rinse → `wash`, combine → `mix`, dress → `toss`, warm/reheat →
   `heat`, blend → `puree`, sprinkle → `dust`, brown → `sear`, top → `layer`, tear/pull → `shred`, crumble → `crush`,
   pit → `deseed`, shell → `peel`, cream → `beat`, fillet → `debone`, tadka → `temper`.

---

## A. Action catalog

Each action defines: `id`, `displayName`, `icon`, `category`, `description`, `aliases`, `actionOn` (`INGREDIENT` | `PROCESS` |
`BOTH` | `NONE`), `actionOnRequired`, `multipleIngredients`, `fields` (see F), `preparationStyleSets` (see D), an optional
`temperatureContext`, and `visualization` (a short visual cue for the step-image prompt).

**🧺 Ingredient Handling**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `add` | ➕ Add | Put an ingredient or prepared component into the pot, pan, bowl or dish. | put in, add in, stir in, mix in |
| `remove` | ➖ Remove | Take an ingredient or component out (e.g. remove whole spices, remove from the pan or from the heat). | take out, discard, remove from heat, take off heat |
| `pour` | 🫗 Pour | Pour a liquid or a liquid component into or over something. | — |
| `transfer` | ↪️ Transfer | Move food to another vessel (e.g. to a baking dish, serving bowl or plate lined with paper towel). | move, place in, put into |
| `measure` | ⚖️ Measure | Measure or weigh out an ingredient before using it. | weigh |
| `divide` | ➗ Divide / Portion | Split one ingredient or prepared mixture into portions (e.g. divide the dough into 8 balls). State the number of portions in the description. | portion, split, divide into portions |
| `season` | 🧂 Season | Add salt, pepper or seasonings to taste. Ingredients are the seasonings; processes are what gets seasoned. | season with, adjust seasoning, salt |
| `stuff` | 🫑 Stuff / Fill | Fill one food with another (e.g. stuff peppers, fill dumplings or parathas). | fill |
| `layer` | 🥞 Layer / Top | Build layers or top a dish with components (e.g. biryani, lasagna, pizza toppings). | top, top with, assemble, arrange in layers |
| `spread` | 🧈 Spread | Spread a soft ingredient or mixture evenly over a surface (e.g. butter on bread, sauce on a pizza base). | smear, apply |
| `skewer` | 🍢 Skewer / Thread | Thread pieces onto skewers (kebabs, satay, tikka). | thread, thread onto skewers |
| `wrap` | 🌯 Wrap / Roll Up | Wrap or roll food in a wrapper, leaf or foil (wraps, spring rolls, parcels). | roll up, enclose, wrap in foil |
| `flip` | 🔄 Flip / Turn | Turn food over so the other side cooks (pancakes, cutlets, steaks). | turn, turn over, toss in pan |

**🧼 Cleaning & Trimming**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `wash` | 🚿 Wash / Rinse | Wash or rinse under water (rice, dal, vegetables, canned beans, or cooked pasta). | rinse, clean under water, rinse under cold water |
| `soak` | 🫧 Soak | Leave submerged in liquid to hydrate or soften (dal, beans, rice, saffron, dried chilies). | steep, hydrate |
| `drain` | 🥅 Drain | Pour off liquid and keep the solids (pasta, soaked dal, canned beans, fried food on paper towels). | drain off, pour off |
| `peel` | 🥔 Peel / Shell | Remove skin, peel or shell (potatoes, garlic, eggs, shrimp, peas). | shell, skin, remove the skin, hull |
| `trim` | ✂️ Trim | Cut away ends, stems, fat or sinew. | top and tail, remove ends, trim fat |
| `core` | 🍎 Core | Remove the core (apples, pears, pineapple, cabbage, tomatoes). | remove the core |
| `deseed` | 🌶️ Deseed / Pit | Remove seeds or pits (chilies, capsicum, tomatoes, dates, olives, cherries). | seed, remove seeds, pit, stone, destone |
| `devein` | 🦐 Devein | Remove the dark vein from shrimp or prawns. | remove the vein |
| `debone` | 🦴 Debone / Fillet | Remove bones from meat, poultry or fish, or cut fish into fillets. | fillet, bone, remove bones |
| `clean` | 🐟 Clean / Gut | Prepare fish or seafood (gut, scale, clean squid) or brush clean mushrooms. | gut, scale, clean the fish |

**🔪 Cutting & Breaking Down**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `cut` | 🔪 Cut | Cut into a named shape or size. Always give the preparation style (e.g. cubed, wedges, halved). Can also cut a prepared component (dough, cake). | cut into |
| `chop` | 🪓 Chop | Chop into irregular pieces; use the style for fineness (finely / roughly). | — |
| `slice` | 🍞 Slice | Cut into slices (onions, bread, roast meat, cake). | cut into slices |
| `dice` | 🎲 Dice | Cut into even cubes. | cube |
| `mince` | 🧄 Mince | Chop very finely (garlic, ginger, herbs, shallots). | very finely chop |
| `grate` | 🧀 Grate | Rub against a grater (cheese, ginger, carrot, coconut, nutmeg). | microplane |
| `shred` | 🥬 Shred / Tear | Cut or tear into thin strips or pieces (cabbage, lettuce, basil, cooked chicken, pulled pork). | tear, pull apart, pull, flake |
| `crush` | 🫙 Crush | Crush or crumble (garlic, cardamom, pepper, biscuits, feta). | smash, bruise, crumble, pound lightly |
| `grind` | ⚙️ Grind | Grind in a grinder or mortar into a powder or paste (spice mixes, masala paste, soaked dal batter). | grind to a paste, mill, powder, make a paste |
| `mash` | 🥔 Mash | Crush soft or cooked food into a mash (potatoes, bananas, cooked dal, avocado). | mash up |
| `puree` | 🌀 Purée / Blend | Blend until smooth in a blender or food processor (soups, sauces, smoothies, chutneys). | blend, blitz, liquidize, process |
| `crack` | 🥚 Crack | Crack open (eggs, nuts, whole pepper). | break eggs |
| `zest` | 🍋 Zest | Remove the coloured outer peel of citrus. | grate the zest |
| `juice` | 🍊 Juice / Squeeze | Squeeze out the juice (lemons, limes, oranges, tamarind pulp). | squeeze, extract juice |
| `separate` | 🍳 Separate | Separate egg whites from yolks. | separate the eggs |
| `pound` | 🔨 Pound / Tenderize | Flatten or tenderize with a mallet or rolling pin (chicken breast, cutlets). | tenderize, flatten, beat flat |
| `score` | 🗡️ Score | Make shallow cuts on the surface (fish, meat for marinade, bread dough). | slash, make cuts, make slits |

**🥣 Mixing & Combining**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `mix` | 🥣 Mix / Combine | Combine ingredients or components until evenly distributed. | combine, blend together, bring together, incorporate |
| `stir` | 🥄 Stir | Move food around with a spoon, usually while cooking so it does not stick; use repeat interval for "every few minutes". | stir occasionally, keep stirring |
| `whisk` | 🥢 Whisk | Beat briskly with a whisk to combine or aerate (eggs, batters, dressings). | — |
| `beat` | 🥚 Beat / Cream | Beat vigorously (eggs, or butter with sugar until light and fluffy). | cream, cream together |
| `whip` | 🍦 Whip | Whip to incorporate air until peaks form (cream, egg whites, aquafaba). | whip to peaks, whip up |
| `fold` | 🫳 Fold | Gently combine with a spatula without knocking out air. | fold in, gently fold |
| `toss` | 🥗 Toss / Dress | Lift and turn to coat evenly (salads with dressing, pasta with sauce, vegetables with oil). | dress, toss together, coat evenly |
| `emulsify` | 🫙 Emulsify | Combine fat and liquid into a stable emulsion (mayonnaise, vinaigrette, hollandaise). | emulsion |

**🧂 Marinating & Coating**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `marinate` | 🍖 Marinate | Coat in a marinade and leave to absorb flavour. Use duration; use temperature only for "in the fridge" style instructions. | marinade, marinate in |
| `brine` | 🧂 Brine | Soak in salted water to season and keep moist. | — |
| `coat` | 🫕 Coat | Cover evenly with a paste, sauce or dry mixture. | coat evenly with, cover evenly |
| `dredge` | 🌾 Dredge | Drag through a dry coating like flour before cooking. | dust in flour, roll in flour |
| `bread` | 🍞 Bread / Crumb | Coat with flour, egg and breadcrumbs (cutlets, schnitzel, katsu). | crumb, breadcrumb, coat in breadcrumbs, crumb coat |
| `batter` | 🍤 Dip in Batter | Dip into a wet batter before frying (pakora, tempura, fish). | dip, dip in batter, coat in batter |
| `rub` | 🤲 Rub | Rub seasoning into food, or rub fat into flour until crumbly. | rub in, massage |
| `brush` | 🖌️ Brush | Apply a thin layer with a brush (egg wash, melted butter, oil, basting juices). | baste, egg wash, brush with |

**♨️ Heating & General Cooking**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `heat` | 🌡️ Heat / Warm | Heat a pan, fat, liquid or dish (heat oil, warm milk, reheat curry). No target needed for "heat the pan". | warm, reheat, heat up, warm up |
| `melt` | 🫠 Melt | Heat until liquid (butter, ghee, chocolate, cheese, jaggery). | — |
| `cook` | 🍳 Cook | General cooking when the recipe does not name a technique. Prefer a specific action (sauté, simmer, fry…) whenever one fits. | cook until done, cook through |

**🍲 Moist-Heat Cooking**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `boil` | ♨️ Boil | Cook in, or bring liquid to, a full boil. | bring to a boil, bring to the boil, rolling boil |
| `simmer` | 🫕 Simmer | Cook gently in liquid just below a boil. | gently boil, let it bubble |
| `steam` | 💨 Steam | Cook with steam over boiling water (idli, dumplings, vegetables, fish). | steam cook |
| `poach` | 🥚 Poach | Cook gently submerged in barely simmering liquid (eggs, fish, chicken, pears). | — |
| `blanch` | 🥦 Blanch | Briefly boil, then usually plunge into ice water (spinach, beans, almonds, tomatoes). | shock, blanch and shock |
| `parboil` | 🍚 Parboil | Partially cook by boiling before finishing another way (biryani rice, roast potatoes). | par-cook, partially cook, half cook |
| `braise` | 🥘 Braise | Sear, then cook slowly, covered, in a little liquid on the stove or in the oven. | pot roast |
| `stew` | 🍲 Stew | Cook small pieces slowly, fully submerged in liquid. | — |
| `pressure-cook` | 🫙 Pressure Cook | Cook in a sealed pressure cooker. Convert "N whistles" to an approximate duration and keep the whistle count in the description. | pressure cooker, instant pot, cook for whistles |
| `slow-cook` | 🐢 Slow Cook | Cook for hours at low heat in a slow cooker. Use heat level low/high for the cooker setting. | crock pot, crockpot, slow cooker |

**🍳 Frying & Sautéing**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `saute` | 🍳 Sauté | Cook quickly in a little fat, moving often (onions, garlic, vegetables). | sauté, saute, sweat, soften, bhuno, cook until soft |
| `fry` | 🍳 Fry | Cook in hot fat in a pan. Prefer deep fry / shallow fry / stir fry / sauté when the recipe is specific. | pan fry, pan-fry, fry until golden |
| `deep-fry` | 🍟 Deep Fry | Submerge fully in hot oil (pakora, samosa, fries, puri). | deep fry, fry in hot oil |
| `shallow-fry` | 🥘 Shallow Fry | Fry in a thin layer of oil, turning once (cutlets, tikki, fish). | shallow fry |
| `stir-fry` | 🥢 Stir Fry | Cook fast over high heat while tossing constantly, usually in a wok. | stir fry, wok fry, toss in wok |
| `sear` | 🥩 Sear / Brown | Brown the surface quickly over high heat. | brown, brown on all sides |
| `temper` | 🌶️ Temper (Tadka) | Fry whole spices and aromatics briefly in hot fat to release flavour (tadka). Also used for tempering eggs or chocolate. | tadka, tarka, chaunk, baghar, bloom spices |
| `caramelize` | 🍯 Caramelize | Cook slowly until sugars turn deep golden brown (onions, sugar). | caramelise, brown slowly |

**🔥 Oven, Grill & Dry Heat**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `preheat` | 🔆 Preheat | Bring the oven, grill or pan up to temperature before cooking. Has no Action On target. | pre-heat, preheat the oven |
| `roast` | 🍗 Roast | Cook with dry heat, usually in the oven, until browned (meat, vegetables). For dry-roasting spices in a pan use Toast. | oven roast |
| `bake` | 🧁 Bake | Cook in the oven with dry heat (bread, cakes, casseroles). | oven bake |
| `grill` | 🍢 Grill / Barbecue | Cook over direct heat on a grill, griddle pan or tandoor. | barbecue, bbq, chargrill, tandoor |
| `broil` | 🔥 Broil | Cook under intense top heat (broiler / oven grill). | salamander, grill from above |
| `char` | 🔥 Char / Blister | Blacken the outside directly over a flame (eggplant for baingan bharta, peppers, tortillas). | blister, fire roast, roast over flame, blacken |
| `toast` | 🍞 Toast / Dry Roast | Brown with dry heat and no (or little) fat (spices, nuts, rava, bread). | dry roast, dry-roast, roast in a dry pan |
| `smoke` | 💨 Smoke | Flavour or cook with smoke (smoker, or dhungar with a hot coal). | dhungar, smoke infuse |
| `air-fry` | 🌬️ Air Fry | Cook in an air fryer; use repeat interval for "shake every N minutes". | air fry, air fryer |
| `microwave` | 📡 Microwave | Heat or cook in a microwave oven. | nuke, zap |

**🥄 Sauces & Liquids**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `reduce` | 📉 Reduce | Simmer uncovered so liquid evaporates and flavour concentrates. | reduce by half, cook down, boil down |
| `deglaze` | 🍷 Deglaze | Add liquid to a hot pan and scrape up the browned bits. | deglaze the pan |
| `thicken` | 🥣 Thicken | Make a liquid thicker, e.g. with a slurry, roux or by cooking down. | thicken with, add a slurry |
| `clarify` | 🧈 Clarify | Remove solids to make a liquid clear (clarified butter/ghee, consommé). | make ghee |
| `strain` | 🥅 Strain / Sieve | Pass through a sieve or cloth to separate liquid from solids (stock, tea, paneer, purées). | sieve, filter, pass through a sieve |
| `skim` | 🥄 Skim | Remove foam, scum or fat from the surface of a liquid. | skim off, remove the scum, remove the foam |

**🥖 Dough & Baking**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `sift` | 🌾 Sift | Pass dry ingredients through a sieve to aerate and remove lumps. | sieve flour |
| `knead` | 👐 Knead | Work dough by pressing and folding until smooth and elastic. | work the dough |
| `proof` | 🫓 Proof / Rise | Let dough rise, or activate yeast in warm liquid. | prove, rise, let rise, activate yeast, bloom yeast |
| `roll` | 🫓 Roll Out | Flatten with a rolling pin (roti, pastry, pasta, cookie dough). State thickness or diameter in the description. | roll out, roll into a circle, sheet |
| `shape` | 🥟 Shape / Form | Form into shapes (balls, patties, loaves, dumplings, koftas). | form, mould, mold, make balls, form patties, pleat |
| `grease` | 🧈 Grease / Line | Coat a pan or tray with fat, or line it with paper. The ingredient is the fat used, if any. | line, oil the pan, line the tray |

**🍽️ Finishing & Serving**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `garnish` | 🌿 Garnish | Add a final topping for flavour and looks (chopped coriander, fried onions, sesame). | decorate, finish with |
| `drizzle` | 💧 Drizzle | Pour a thin stream over the top (oil, cream, honey, sauce). | drizzle over |
| `glaze` | ✨ Glaze | Coat with a shiny layer (sugar glaze, reduced sauce, jam). | glaze with |
| `dust` | ❄️ Dust / Sprinkle | Scatter a light, even layer of a dry ingredient (icing sugar, cocoa, chaat masala, cheese). | sprinkle, sprinkle over, scatter |
| `plate` | 🍽️ Plate / Arrange | Arrange food on the serving plate or platter. | arrange, dish up, assemble on a plate |
| `serve` | 🍛 Serve | Serve the finished dish; put accompaniments and "hot/chilled" in the description. | serve hot, serve immediately, serve with |

**🫙 Fermenting & Preserving**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `pickle` | 🥒 Pickle | Preserve or flavour in vinegar, brine or spiced oil. | quick pickle, achar |
| `ferment` | 🫧 Ferment | Leave to ferment (idli/dosa batter, yogurt, kimchi, sourdough). | leave to ferment, culture, set curd |
| `dry` | ☀️ Dry / Dehydrate | Remove moisture: pat dry with paper towel, air-dry, sun-dry or dehydrate. | dehydrate, sun dry, pat dry, air dry |
| `cure` | 🥓 Cure | Preserve with salt, sugar or nitrates (gravlax, bacon). | salt cure |
| `preserve` | 🫙 Preserve / Jar | Seal in sterilised jars for storage (jams, chutneys, confit). | can, bottle, jar, make jam |

**⏳ Timing & Temperature Control**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `wait` | ⏱️ Wait | Pause for a period without doing anything. | leave, set aside, let it sit, keep aside |
| `rest` | 😴 Rest | Leave food undisturbed so it relaxes, sets or redistributes juices (dough, meat, batter). | let rest, rest the dough, rest the meat |
| `chill` | 🧊 Chill / Refrigerate | Keep in the refrigerator or an ice bath to cool or set. | refrigerate, fridge, cool in the fridge |
| `cool` | 🌬️ Cool | Let food come down in temperature, usually to room temperature. | let cool, cool down, bring to room temperature |
| `freeze` | ❄️ Freeze | Freeze until solid or firm. | put in the freezer |
| `thaw` | 💧 Thaw / Defrost | Bring frozen food back to a workable state. | defrost |

**✨ Custom**

| id | Name | Description | Aliases (input only) |
|---|---|---|---|
| `custom` | ✨ Custom Action | Only when no catalog action can represent the operation. Always give a customActionName. | — |

**Taxonomy decisions, and where this deviates from the brief:**

- **`cut`, `slice`, `shred`, `mash`, `puree`, `grind` also accept subprocess outputs.** "Slice the baked loaf", "cut the rolled dough
  into circles", "shred the cooked chicken", "mash the boiled potatoes", and "grind the roasted spices" are all common, and the thing
  being cut is often a subprocess result. Fine knife-prep actions stay ingredient-only: `chop`, `dice`, `mince`, `grate`, `peel`,
  `devein`, and similar.
- **`preheat` is `NONE`**, because it acts on equipment. `heat`, `wait` and `grease` accept targets but do not require them: "heat the pan",
  "wait 5 minutes", "line the tray".
- **Added because real recipes need them constantly:** `transfer`, `flip`, `preheat`, `cook`, `melt`, `caramelize`, `sift`, `grease`,
  `brush`, `spread`, `layer`, `stuff`, `skewer`, `wrap`, `air-fry`, `microwave`, `pound`, `separate`, `score`. Without these, a typical recipe falls back to `custom`.
- **`ferment`, `rest` and `cure` appear only once** even though the brief lists them in two groups. Categories are used only for display, so a
  second copy would just be a duplicate id.
- **Indian technique coverage:** `temper` (tadka / chaunk / baghar), `pressure-cook` (whistles are converted to an approximate
  duration, and the count is kept in the description), `smoke` (dhungar), `grind` (masala paste, batter), `char` (baingan bharta),
  `toast` (dry-roasting spices or rava).

## B. Ingredient taxonomy and initial catalog

Each ingredient defines: `id`, `name`, `category`, `icon`, `defaultUnit`, `units` (the default unit first, then reasonable
alternatives), optional `aliases`, and optional `preparationStyleSets` to override its category. The UI offers every catalog ingredient,
whatever the Kitchen inventory holds. `custom` plus a `customIngredientName` covers anything missing, and the name is now preserved
end to end.

| Category | Default unit · allowed units | Prep-style sets | Ingredients |
|---|---|---|---|
| 💧 **Water & Liquids** (`liquids`, 15) | ml · ml, l, cup, tbsp, tsp, fl-oz | condition | `water`, `ice`, `coconut-water`, `sparkling-water`, `lemon-juice`, `lime-juice`, `orange-juice`, `white-wine`, `red-wine`, `beer`, `rice-wine`, `brewed-tea`, `brewed-coffee`, `tea-leaves`, `instant-coffee` |
| 🫗 **Oils & Fats** (`oils-fats`, 15) | tbsp · tbsp, tsp, ml, cup, g, as-needed | condition | `oil`, `vegetable-oil`, `sunflower-oil`, `canola-oil`, `olive-oil`, `extra-virgin-olive-oil`, `mustard-oil`, `coconut-oil`, `sesame-oil`, `peanut-oil`, `avocado-oil`, `ghee`, `lard`, `shortening`, `cooking-spray` |
| 🧂 **Salt & Seasonings** (`salt-seasonings`, 6) | tsp · tsp, tbsp, g, pinch, to-taste | — | `salt`, `sea-salt`, `kosher-salt`, `black-salt`, `rock-salt`, `msg` |
| 🌶️ **Spices & Spice Blends** (`spices`, 54) | tsp · tsp, tbsp, g, pinch, to-taste | form, crush, condition | `black-pepper`, `white-pepper`, `cumin-seeds`, `cumin-powder`, `roasted-cumin-powder`, `coriander-seeds`, `coriander-powder`, `turmeric`, `red-chili-powder`, `kashmiri-chili-powder`, `paprika`, `smoked-paprika`, `cayenne`, `chili-flakes`, `dried-red-chili`, `mustard-seeds`, `fenugreek-seeds`, `fennel-seeds`, `carom-seeds`, `nigella-seeds`, `caraway-seeds`, `green-cardamom`, `black-cardamom`, `cardamom-powder`, `whole-cloves`, `cinnamon-stick`, `ground-cinnamon`, `bay-leaf`, `star-anise`, `nutmeg`, `mace`, `saffron`, `asafoetida`, `kasuri-methi`, `ginger-powder`, `garlic-powder`, `onion-powder`, `amchur`, `allspice`, `sumac`, `dried-oregano`, `dried-thyme`, `garam-masala`, `chaat-masala`, `curry-powder`, `sambar-powder`, `tandoori-masala`, `pav-bhaji-masala`, `biryani-masala`, `five-spice`, `zaatar`, `italian-seasoning`, `cajun-seasoning`, `taco-seasoning` |
| 🌿 **Fresh Herbs** (`herbs`, 15) | tbsp · tbsp, cup, handful, sprig, bunch, leaf, g | chop, mince, slice, shape, break, texture, form, condition | `cilantro`, `mint`, `parsley`, `basil`, `thai-basil`, `dill`, `thyme`, `rosemary`, `oregano`, `sage`, `chives`, `tarragon`, `curry-leaves`, `fenugreek-leaves`, `kaffir-lime-leaves` |
| 🧅 **Aromatics** (`aromatics`, 12) | piece · piece, g, tbsp, tsp, cup | size, chop, mince, slice, dice, shape, grate, crush, break, texture, form, condition | `onion`, `red-onion`, `shallot`, `spring-onion`, `leek`, `garlic`, `ginger`, `chili`, `jalapeno`, `red-chili-fresh`, `galangal`, `lemongrass` |
| 🥕 **Vegetables** (`vegetables`, 43) | piece · piece, g, kg, cup, lb | size, chop, mince, slice, dice, shape, grate, crush, break, texture, form, condition | `tomato`, `cherry-tomato`, `potato`, `sweet-potato`, `carrot`, `capsicum`, `cucumber`, `zucchini`, `eggplant`, `cauliflower`, `broccoli`, `cabbage`, `red-cabbage`, `spinach`, `lettuce`, `kale`, `bok-choy`, `peas`, `corn`, `baby-corn`, `green-beans`, `okra`, `mushroom`, `shiitake`, `pumpkin`, `butternut-squash`, `bottle-gourd`, `bitter-gourd`, `ridge-gourd`, `drumstick`, `raw-banana`, `beetroot`, `radish`, `turnip`, `celery`, `asparagus`, `brussels-sprouts`, `bean-sprouts`, `artichoke`, `fennel-bulb`, `yam`, `taro`, `jackfruit` |
| 🍎 **Fruits** (`fruits`, 22) | piece · piece, g, cup, kg | size, chop, mince, slice, dice, shape, grate, crush, break, texture, form, condition | `lemon`, `lime`, `orange`, `apple`, `banana`, `mango`, `raw-mango`, `pineapple`, `strawberry`, `blueberry`, `raspberry`, `grapes`, `pomegranate`, `watermelon`, `papaya`, `cherry`, `peach`, `pear`, `kiwi`, `avocado`, `coconut`, `tamarind` |
| 🌾 **Grains & Cereals** (`grains`, 10) | cup · cup, g, kg, tbsp | condition | `oats`, `semolina`, `quinoa`, `couscous`, `bulgur`, `barley`, `millet`, `poha`, `cornmeal`, `puffed-rice` |
| 🍚 **Rice** (`rice`, 8) | cup · cup, g, kg | condition | `rice`, `basmati-rice`, `jasmine-rice`, `brown-rice`, `sushi-rice`, `arborio-rice`, `glutinous-rice`, `idli-rice` |
| 🍝 **Pasta & Noodles** (`pasta-noodles`, 14) | g · g, kg, cup, packet, oz, lb | condition, break | `pasta`, `spaghetti`, `penne`, `macaroni`, `fusilli`, `fettuccine`, `lasagna-sheets`, `egg-noodles`, `rice-noodles`, `ramen-noodles`, `udon`, `soba`, `glass-noodles`, `vermicelli` |
| 🌾 **Flours & Starches** (`flours-starches`, 13) | cup · cup, g, kg, tbsp, tsp | condition | `all-purpose-flour`, `whole-wheat-flour`, `bread-flour`, `cake-flour`, `self-raising-flour`, `chickpea-flour`, `rice-flour`, `cornstarch`, `almond-flour`, `tapioca-starch`, `potato-starch`, `millet-flour`, `sabudana` |
| 🧁 **Baking Agents & Essentials** (`baking`, 17) | tsp · tsp, tbsp, g, packet, drop | condition, chop, grate | `baking-powder`, `baking-soda`, `yeast`, `cream-of-tartar`, `vanilla-extract`, `vanilla-bean`, `cocoa-powder`, `dark-chocolate`, `milk-chocolate`, `white-chocolate`, `chocolate-chips`, `gelatin`, `agar-agar`, `food-coloring`, `sprinkles`, `rose-water`, `kewra-water` |
| 🫘 **Legumes, Beans & Lentils** (`legumes`, 16) | cup · cup, g, kg, can | condition, texture, crush | `chickpeas`, `kala-chana`, `kidney-beans`, `black-beans`, `white-beans`, `black-eyed-peas`, `toor-dal`, `moong-dal`, `whole-moong`, `masoor-dal`, `brown-lentils`, `chana-dal`, `urad-dal`, `whole-urad`, `soybeans`, `edamame` |
| 🥜 **Nuts, Seeds & Dried Fruit** (`nuts-seeds`, 21) | tbsp · tbsp, cup, g, piece, handful, tsp | chop, slice, crush, texture, form, condition | `almonds`, `cashews`, `peanuts`, `walnuts`, `pistachios`, `pine-nuts`, `hazelnuts`, `pecans`, `melon-seeds`, `sesame-seeds`, `poppy-seeds`, `sunflower-seeds`, `pumpkin-seeds`, `chia-seeds`, `flax-seeds`, `desiccated-coconut`, `raisins`, `dates`, `dried-apricots`, `dried-cranberries`, `lotus-seeds` |
| 🥛 **Dairy & Alternatives** (`dairy`, 22) | g · g, cup, tbsp, ml, kg | size, chop, slice, dice, grate, break, condition | `milk`, `butter`, `cream`, `sour-cream`, `yogurt`, `greek-yogurt`, `buttermilk`, `condensed-milk`, `evaporated-milk`, `milk-powder`, `khoya`, `paneer`, `cheese`, `cheddar`, `mozzarella`, `parmesan`, `feta`, `cream-cheese`, `ricotta`, `coconut-milk`, `coconut-cream`, `plant-milk` |
| 🥚 **Eggs** (`eggs`, 3) | piece · piece | chop, slice, shape, form, condition | `egg`, `egg-white`, `egg-yolk` |
| 🍗 **Poultry** (`poultry`, 8) | g · g, kg, lb, piece, oz | size, chop, mince, slice, dice, shape, grate, break, form, condition | `chicken`, `chicken-breast`, `chicken-thigh`, `chicken-drumstick`, `chicken-wings`, `ground-chicken`, `turkey`, `duck` |
| 🥩 **Meat** (`meat`, 12) | g · g, kg, lb, piece, oz, slice | size, chop, mince, slice, dice, shape, grate, break, form, condition | `beef`, `beef-steak`, `ground-beef`, `lamb`, `goat`, `ground-lamb`, `pork`, `pork-belly`, `ground-pork`, `bacon`, `ham`, `sausage` |
| 🐟 **Fish & Seafood** (`seafood`, 13) | g · g, kg, lb, piece, fillet, oz | size, chop, mince, slice, dice, shape, grate, break, form, condition | `fish`, `salmon`, `tuna`, `cod`, `mackerel`, `sardines`, `anchovies`, `shrimp`, `crab`, `lobster`, `squid`, `mussels`, `scallops` |
| 🌱 **Plant Proteins** (`plant-proteins`, 4) | g · g, cup, piece, packet | size, chop, slice, dice, shape, grate, break, condition | `tofu`, `tempeh`, `seitan`, `soya-chunks` |
| 🍯 **Sweeteners** (`sweeteners`, 9) | tsp · tsp, tbsp, cup, g | condition, grate, crush | `sugar`, `brown-sugar`, `powdered-sugar`, `jaggery`, `palm-sugar`, `honey`, `maple-syrup`, `golden-syrup`, `sugar-syrup` |
| 🥫 **Condiments & Sauces** (`condiments`, 26) | tbsp · tbsp, tsp, ml, cup, dash | — | `soy-sauce`, `dark-soy-sauce`, `oyster-sauce`, `fish-sauce`, `hoisin-sauce`, `hot-sauce`, `sweet-chili-sauce`, `ketchup`, `mustard`, `mayonnaise`, `worcestershire-sauce`, `bbq-sauce`, `vinegar`, `apple-cider-vinegar`, `balsamic-vinegar`, `rice-vinegar`, `red-wine-vinegar`, `mirin`, `tahini`, `peanut-butter`, `pesto`, `green-chutney`, `tamarind-chutney`, `tamarind-paste`, `salsa`, `chili-oil` |
| 🫙 **Fermented & Pickled** (`fermented`, 12) | tbsp · tbsp, tsp, cup, g, piece | chop, slice, form, condition | `kimchi`, `sauerkraut`, `miso`, `gochujang`, `doubanjiang`, `black-bean-sauce`, `indian-pickle`, `pickled-cucumber`, `olives`, `capers`, `sourdough-starter`, `idli-batter` |
| 🍲 **Stock & Broth** (`stock-broth`, 6) | ml · ml, l, cup | condition | `vegetable-stock`, `chicken-stock`, `beef-stock`, `fish-stock`, `dashi`, `stock-cube` |
| 🥡 **Ready & Composite Ingredients** (`composite`, 23) | g · g, cup, tbsp, piece, packet | size, chop, slice, dice, shape, break, form, condition | `tomato-puree`, `tomato-paste`, `tomato-sauce`, `canned-tomatoes`, `ginger-garlic-paste`, `garlic-paste`, `ginger-paste`, `curry-paste`, `harissa`, `fried-onions`, `dough`, `puff-pastry`, `pie-crust`, `bread`, `burger-bun`, `tortilla`, `flatbread`, `pizza-base`, `breadcrumbs`, `wrappers`, `biscuits`, `custard-powder`, `jam` |
| ✨ **Other** (`other`, 1) | piece · all | size, chop, mince, slice, dice, shape, grate, crush, break, texture, form, condition | `custom` |

**How the catalog grows:** add a row to the right category. Give it a default unit and only the units people actually use. Add aliases for regional names.
Leave `preparationStyleSets` off unless the ingredient differs from its category. For example, a whole spice such as `bay-leaf` inherits `form, crush, condition` from Spices.

## C. Unit catalog

Each unit defines: `id`, `label`, `shortLabel`, `category`, `quantifiable`, and `aliases`. Every unit here is valid for an ingredient quantity. Durations use
their own units (`seconds`, `minutes`, `hours`).

| Category | Units (id — quantifiable) |
|---|---|
| Weight (`weight`) | `mg`, `g`, `kg`, `oz`, `lb` |
| Volume (`volume`) | `ml`, `l`, `fl-oz`, `pint`, `quart` |
| Spoon & Cup (`spoon`) | `tsp`, `tbsp`, `cup` |
| Count & Pieces (`count`) | `piece`, `clove`, `slice`, `sprig`, `bunch`, `leaf`, `stalk`, `head`, `fillet`, `stick`, `sheet`, `cube` |
| Length (`length`) | `inch`, `cm` |
| Small Amounts (`small`) | `pinch`, `dash`, `drop`, `handful` |
| Packages & Containers (`container`) | `can`, `jar`, `packet`, `bottle` |
| Non-numeric (`non-numeric`) | `to-taste` (no number), `as-needed` (no number) |
| Other (`other`) | `custom` |

- **Removed ambiguity:** *package / pack / box / bag / sachet* are aliases of `packet`, and *tin* is an alias of `can`. `stalk` covers celery ribs.
- **Non-numeric units** (`to-taste`, `as-needed`) allow `quantity: null`. This covers "salt to taste" and "oil for deep frying", which
  previously had to be faked as `1 COUNT`.
- **`inch` / `cm`** are piece lengths ("1 inch ginger", "2 inch cinnamon stick").
- **Legacy mapping** is handled by aliases, with no special-case code: `COUNT` → `piece`, `GRAM` → `g`, `KG` → `kg`, `ML` → `ml`, `LITER` → `l`.

## D. Preparation style catalog

Each style defines: `id`, `label`, and `aliases`. Styles are grouped into named **sets**. An **action** lists the sets it can use, and an **ingredient
category** lists the sets that make sense for it. The styles offered for one ingredient on one step are the
**intersection** of the two. For example, with `cut`, `onion` gets every cut style while `water` gets none. With `boil`,
`potato` gets form, condition and cut styles, while `salt` gets none. `custom` is always available when the action takes a style.

| Set | Styles |
|---|---|
| `size` — Piece size | `fine`, `medium`, `large` |
| `chop` — Chopped | `chopped`, `finely-chopped`, `rough-chop`, `bite-sized`, `chunks` |
| `mince` — Minced | `minced`, `finely-minced` |
| `slice` — Sliced | `sliced`, `thin-slice`, `thick-slice`, `diagonal`, `rings`, `strips` |
| `dice` — Diced | `diced`, `finely-diced`, `cubed` |
| `shape` — Shaped cuts | `julienne`, `chiffonade`, `wedges`, `halved`, `quartered`, `florets`, `strips` |
| `grate` — Grated / shredded | `grated`, `finely-grated`, `coarsely-grated`, `shredded` |
| `crush` — Crushed / ground | `crushed`, `cracked`, `coarsely-ground`, `finely-ground` |
| `break` — Broken by hand | `crumbled`, `torn`, `flaked` |
| `texture` — Purée / paste texture | `smooth`, `chunky`, `paste` |
| `form` — Form as used | `whole`, `halved`, `quartered`, `peeled`, `unpeeled`, `seeded`, `trimmed`, `cored`, `pitted`, `shelled`, `stemmed`, `boneless`, `bone-in`, `skinless`, `skin-on`, `butterflied`, `deveined` |
| `condition` — Condition | `room-temperature`, `softened`, `melted`, `chilled`, `frozen`, `thawed`, `beaten`, `sifted`, `packed`, `drained`, `rinsed`, `soaked`, `cooked`, `dried` |

- The v1 ids `fine`, `medium` and `large` remain the generic `size` set, and `thin-slice`, `thick-slice`, `rough-chop` and `julienne` keep their ids with clearer labels.
- Cutting actions produce a style. `cut` requires one, and `chop`, `slice`, `dice`, `mince`, `grate`, `crush`, `grind`, `mash` and `puree`
  each accept only their own sets. Cooking and assembly actions (`add`, `marinate`, `fry`, `roast`, `garnish` …) accept the
  "ingredient state" sets (form, condition and the cut shapes) to record how the ingredient *arrives*. That way "add 500 g boneless
  chicken" or "roast halved tomatoes" keeps its state. `stir`, `mix`, `wait`, `season`, `strain` and similar actions never show a
  preparation style.

## E. Heat level and temperature catalog

**Heat levels** (`flameLevels`, id kept for compatibility, labelled "Heat Level" in the UI): `off`, `very-low`, `low`,
`medium-low`, `medium`, `medium-high`, `high`, `very-high`, `custom`. They describe **burner or appliance setting**.

**Temperature** is a separate concept: a **numeric value plus a unit** (`C` / `F`), stored as `temperatureValue` and
`temperatureUnit` on the step. It is never encoded into action names. Each action that accepts temperature also declares what the number
**means**:

| `temperatureContext` | Meaning | Examples |
|---|---|---|
| `oven` | air temperature of the oven, smoker or air fryer | bake, roast, braise, air-fry, preheat |
| `oil` | temperature of the frying fat | fry, deep-fry, shallow-fry |
| `liquid` | temperature of the cooking or soaking liquid | boil, simmer, poach, soak |
| `cooking-surface` | pan, griddle or grill surface | heat, cook, grill, toast |
| `ambient` | fridge, freezer, warm place, room temperature | marinate, proof, ferment, chill, cool, freeze |

The UI uses the context as the field label, for example "Oven Temperature". The AI prompt includes it so the model does not put a pan heat level into an oven
temperature.

## F. Action → allowed fields matrix

● required · ◐ recommended (strongly applicable; the AI should fill it when the recipe states it) · ○ optional · — not applicable
(hidden in the UI and rejected by the AI validator).
Quantity + unit and preparation style are **per Action On ingredient**. Temperature, heat level, duration and repeat interval are **per step**.

| Action | Qty + Unit | Prep style (sets) | Temperature (context) | Heat level | Duration | Repeat every |
|---|:-:|---|---|:-:|:-:|:-:|
| `add` | ● | ○ all ingredient-state sets | — | — | — | — |
| `remove` | ○ | — | — | — | — | — |
| `pour` | ● | — | — | — | — | — |
| `transfer` | — | — | — | — | — | — |
| `measure` | ● | ○ condition | — | — | — | — |
| `divide` | — | — | — | — | — | — |
| `season` | ○ | — | — | — | — | — |
| `stuff` | ○ | ○ all ingredient-state sets | — | — | — | — |
| `layer` | ○ | ○ all ingredient-state sets | — | — | — | — |
| `spread` | ○ | — | — | — | — | — |
| `skewer` | ○ | ○ all ingredient-state sets | — | — | — | — |
| `wrap` | — | — | — | — | — | — |
| `flip` | — | — | — | — | — | ○ |
| `wash` | ○ | ○ form, condition | — | — | — | — |
| `soak` | ○ | ○ form, condition | ○ liquid | — | ◐ | — |
| `drain` | — | — | — | — | — | — |
| `peel` | ○ | — | — | — | — | — |
| `trim` | ○ | — | — | — | — | — |
| `core` | ○ | — | — | — | — | — |
| `deseed` | ○ | — | — | — | — | — |
| `devein` | ○ | — | — | — | — | — |
| `debone` | ○ | — | — | — | — | — |
| `clean` | ○ | — | — | — | — | — |
| `cut` | ○ | ● size, chop, mince, slice, dice, shape | — | — | — | — |
| `chop` | ○ | ○ size, chop | — | — | — | — |
| `slice` | ○ | ○ slice | — | — | — | — |
| `dice` | ○ | ○ dice, size | — | — | — | — |
| `mince` | ○ | ○ mince | — | — | — | — |
| `grate` | ○ | ○ grate | — | — | — | — |
| `shred` | ○ | ○ grate, break | — | — | — | — |
| `crush` | ○ | ○ crush, break | — | — | — | — |
| `grind` | ○ | ○ crush, texture | — | — | ○ | — |
| `mash` | ○ | ○ texture | — | — | — | — |
| `puree` | ○ | ○ texture | — | — | ○ | — |
| `crack` | ○ | — | — | — | — | — |
| `zest` | ○ | — | — | — | — | — |
| `juice` | ○ | — | — | — | — | — |
| `separate` | ○ | — | — | — | — | — |
| `pound` | ○ | — | — | — | — | — |
| `score` | — | — | — | — | — | — |
| `mix` | ○ | — | — | — | ○ | ○ |
| `stir` | ○ | — | — | ○ | ○ | ◐ |
| `whisk` | ○ | — | — | — | ○ | ○ |
| `beat` | ○ | — | — | — | ○ | — |
| `whip` | ○ | — | — | — | ○ | — |
| `fold` | ○ | — | — | — | — | — |
| `toss` | ○ | — | — | — | — | — |
| `emulsify` | ○ | — | — | — | ○ | — |
| `marinate` | ○ | ○ all ingredient-state sets | ○ ambient | — | ◐ | — |
| `brine` | ○ | ○ form | — | — | ◐ | — |
| `coat` | ○ | ○ all ingredient-state sets | — | — | — | — |
| `dredge` | ○ | ○ all ingredient-state sets | — | — | — | — |
| `bread` | ○ | ○ all ingredient-state sets | — | — | — | — |
| `batter` | ○ | ○ all ingredient-state sets | — | — | — | — |
| `rub` | ○ | ○ all ingredient-state sets | — | — | — | — |
| `brush` | ○ | — | — | — | — | — |
| `heat` | ○ | — | ○ cooking-surface | ◐ | ○ | — |
| `melt` | ○ | — | — | ○ | ○ | — |
| `cook` | ○ | ○ all ingredient-state sets | ○ cooking-surface | ○ | ○ | ○ |
| `boil` | ○ | ○ all ingredient-state sets | ○ liquid | ○ | ◐ | ○ |
| `simmer` | ○ | ○ all ingredient-state sets | ○ liquid | ◐ | ◐ | ○ |
| `steam` | ○ | ○ all ingredient-state sets | — | ○ | ◐ | — |
| `poach` | ○ | ○ all ingredient-state sets | ○ liquid | ○ | ◐ | — |
| `blanch` | ○ | ○ all ingredient-state sets | — | ○ | ◐ | — |
| `parboil` | ○ | ○ all ingredient-state sets | — | ○ | ◐ | — |
| `braise` | ○ | ○ all ingredient-state sets | ○ oven | ○ | ◐ | — |
| `stew` | ○ | ○ all ingredient-state sets | — | ○ | ◐ | ○ |
| `pressure-cook` | ○ | ○ all ingredient-state sets | — | ○ | ◐ | — |
| `slow-cook` | ○ | ○ all ingredient-state sets | — | ○ | ◐ | — |
| `saute` | ○ | ○ all ingredient-state sets | — | ◐ | ○ | ○ |
| `fry` | ○ | ○ all ingredient-state sets | ○ oil | ○ | ○ | ○ |
| `deep-fry` | ○ | ○ all ingredient-state sets | ◐ oil | ○ | ○ | — |
| `shallow-fry` | ○ | ○ all ingredient-state sets | ○ oil | ○ | ○ | ○ |
| `stir-fry` | ○ | ○ all ingredient-state sets | — | ◐ | ○ | — |
| `sear` | ○ | ○ all ingredient-state sets | — | ◐ | ○ | — |
| `temper` | ○ | — | — | ○ | ○ | — |
| `caramelize` | ○ | ○ all ingredient-state sets | — | ◐ | ◐ | — |
| `preheat` | — | — | ◐ oven | ○ | — | — |
| `roast` | ○ | ○ all ingredient-state sets | ○ oven | ○ | ◐ | — |
| `bake` | ○ | ○ all ingredient-state sets | ◐ oven | — | ◐ | — |
| `grill` | ○ | ○ all ingredient-state sets | ○ cooking-surface | ○ | ◐ | ○ |
| `broil` | ○ | ○ all ingredient-state sets | — | ○ | ◐ | — |
| `char` | ○ | ○ form | — | ○ | ○ | — |
| `toast` | ○ | ○ all ingredient-state sets | ○ cooking-surface | ○ | ○ | — |
| `smoke` | ○ | ○ all ingredient-state sets | ○ oven | — | ◐ | — |
| `air-fry` | ○ | ○ all ingredient-state sets | ◐ oven | — | ◐ | ○ |
| `microwave` | ○ | — | — | — | ◐ | — |
| `reduce` | — | — | — | ○ | ○ | — |
| `deglaze` | ○ | — | — | ○ | — | — |
| `thicken` | ○ | — | — | ○ | ○ | — |
| `clarify` | — | — | — | ○ | ○ | — |
| `strain` | — | — | — | — | — | — |
| `skim` | — | — | — | — | — | — |
| `sift` | ○ | — | — | — | — | — |
| `knead` | ○ | — | — | — | ◐ | — |
| `proof` | — | — | ○ ambient | — | ◐ | — |
| `roll` | — | — | — | — | — | — |
| `shape` | — | — | — | — | — | — |
| `grease` | ○ | — | — | — | — | — |
| `garnish` | ○ | ○ all ingredient-state sets | — | — | — | — |
| `drizzle` | ○ | — | — | — | — | — |
| `glaze` | ○ | — | — | — | — | — |
| `dust` | ○ | — | — | — | — | — |
| `plate` | — | — | — | — | — | — |
| `serve` | — | — | — | — | — | — |
| `pickle` | ○ | ○ size, chop, slice, dice, shape, form | — | — | ○ | — |
| `ferment` | — | — | ○ ambient | — | ◐ | — |
| `dry` | — | ○ form, slice | ○ oven | — | ○ | — |
| `cure` | ○ | ○ form | — | — | ◐ | — |
| `preserve` | — | ○ size, chop, slice, form | — | — | ○ | — |
| `wait` | — | — | — | — | ◐ | — |
| `rest` | — | — | — | — | ◐ | — |
| `chill` | — | — | ○ ambient | — | ◐ | — |
| `cool` | — | — | ○ ambient | — | ○ | — |
| `freeze` | — | — | ○ ambient | — | ◐ | — |
| `thaw` | — | — | — | — | ○ | — |
| `custom` | ○ | ○ all ingredient-state sets | ○ | ○ | ○ | ○ |

Validator semantics:
- A **step-level** field (temperature, heat level, duration, repeat interval) is rejected when the action does not declare it.
- A **preparation style** is rejected when it is outside the action's allowed sets. The error message lists the allowed ids, so the
  retry is cheap.
- **Quantity** is required only when the action marks it required (`add`, `pour`, `measure`), and even then it may be `null` when the
  unit is non-numeric. For other actions an extra quantity is tolerated rather than rejected: repeating an ingredient's amount on a later step
  is harmless, and rejecting it would cause needless retries.

## G. Action → allowed Action On targets

| Action | Ingredients | Subprocess outputs | Multiple ingredients | Target required |
|---|:-:|:-:|:-:|:-:|
| `add` | ✓ | ✓ | ✓ | yes |
| `remove` | ✓ | ✓ | ✓ | yes |
| `pour` | ✓ | ✓ | ✓ | yes |
| `transfer` | ✓ | ✓ | ✓ | yes |
| `measure` | ✓ | — | ✓ | yes |
| `divide` | ✓ | ✓ | single | yes |
| `season` | ✓ | ✓ | ✓ | yes |
| `stuff` | ✓ | ✓ | ✓ | yes |
| `layer` | ✓ | ✓ | ✓ | yes |
| `spread` | ✓ | ✓ | ✓ | yes |
| `skewer` | ✓ | ✓ | ✓ | yes |
| `wrap` | ✓ | ✓ | ✓ | yes |
| `flip` | ✓ | ✓ | ✓ | yes |
| `wash` | ✓ | ✓ | ✓ | yes |
| `soak` | ✓ | ✓ | ✓ | yes |
| `drain` | ✓ | ✓ | ✓ | yes |
| `peel` | ✓ | — | ✓ | yes |
| `trim` | ✓ | — | ✓ | yes |
| `core` | ✓ | — | ✓ | yes |
| `deseed` | ✓ | — | ✓ | yes |
| `devein` | ✓ | — | ✓ | yes |
| `debone` | ✓ | — | ✓ | yes |
| `clean` | ✓ | — | ✓ | yes |
| `cut` | ✓ | ✓ | ✓ | yes |
| `chop` | ✓ | — | ✓ | yes |
| `slice` | ✓ | ✓ | ✓ | yes |
| `dice` | ✓ | — | ✓ | yes |
| `mince` | ✓ | — | ✓ | yes |
| `grate` | ✓ | — | ✓ | yes |
| `shred` | ✓ | ✓ | ✓ | yes |
| `crush` | ✓ | — | ✓ | yes |
| `grind` | ✓ | ✓ | ✓ | yes |
| `mash` | ✓ | ✓ | ✓ | yes |
| `puree` | ✓ | ✓ | ✓ | yes |
| `crack` | ✓ | — | ✓ | yes |
| `zest` | ✓ | — | ✓ | yes |
| `juice` | ✓ | — | ✓ | yes |
| `separate` | ✓ | — | single | yes |
| `pound` | ✓ | — | ✓ | yes |
| `score` | ✓ | ✓ | ✓ | yes |
| `mix` | ✓ | ✓ | ✓ | yes |
| `stir` | ✓ | ✓ | ✓ | yes |
| `whisk` | ✓ | ✓ | ✓ | yes |
| `beat` | ✓ | ✓ | ✓ | yes |
| `whip` | ✓ | ✓ | ✓ | yes |
| `fold` | ✓ | ✓ | ✓ | yes |
| `toss` | ✓ | ✓ | ✓ | yes |
| `emulsify` | ✓ | ✓ | ✓ | yes |
| `marinate` | ✓ | ✓ | ✓ | yes |
| `brine` | ✓ | ✓ | ✓ | yes |
| `coat` | ✓ | ✓ | ✓ | yes |
| `dredge` | ✓ | ✓ | ✓ | yes |
| `bread` | ✓ | ✓ | ✓ | yes |
| `batter` | ✓ | ✓ | ✓ | yes |
| `rub` | ✓ | ✓ | ✓ | yes |
| `brush` | ✓ | ✓ | ✓ | yes |
| `heat` | ✓ | ✓ | ✓ | no |
| `melt` | ✓ | ✓ | ✓ | yes |
| `cook` | ✓ | ✓ | ✓ | yes |
| `boil` | ✓ | ✓ | ✓ | yes |
| `simmer` | ✓ | ✓ | ✓ | yes |
| `steam` | ✓ | ✓ | ✓ | yes |
| `poach` | ✓ | ✓ | ✓ | yes |
| `blanch` | ✓ | — | ✓ | yes |
| `parboil` | ✓ | ✓ | ✓ | yes |
| `braise` | ✓ | ✓ | ✓ | yes |
| `stew` | ✓ | ✓ | ✓ | yes |
| `pressure-cook` | ✓ | ✓ | ✓ | yes |
| `slow-cook` | ✓ | ✓ | ✓ | yes |
| `saute` | ✓ | ✓ | ✓ | yes |
| `fry` | ✓ | ✓ | ✓ | yes |
| `deep-fry` | ✓ | ✓ | ✓ | yes |
| `shallow-fry` | ✓ | ✓ | ✓ | yes |
| `stir-fry` | ✓ | ✓ | ✓ | yes |
| `sear` | ✓ | ✓ | ✓ | yes |
| `temper` | ✓ | — | ✓ | yes |
| `caramelize` | ✓ | ✓ | ✓ | yes |
| `preheat` | — | — | — | no |
| `roast` | ✓ | ✓ | ✓ | yes |
| `bake` | ✓ | ✓ | ✓ | yes |
| `grill` | ✓ | ✓ | ✓ | yes |
| `broil` | ✓ | ✓ | ✓ | yes |
| `char` | ✓ | — | ✓ | yes |
| `toast` | ✓ | ✓ | ✓ | yes |
| `smoke` | ✓ | ✓ | ✓ | yes |
| `air-fry` | ✓ | ✓ | ✓ | yes |
| `microwave` | ✓ | ✓ | ✓ | yes |
| `reduce` | ✓ | ✓ | ✓ | yes |
| `deglaze` | ✓ | ✓ | ✓ | yes |
| `thicken` | ✓ | ✓ | ✓ | yes |
| `clarify` | ✓ | ✓ | ✓ | yes |
| `strain` | ✓ | ✓ | ✓ | yes |
| `skim` | ✓ | ✓ | ✓ | yes |
| `sift` | ✓ | — | ✓ | yes |
| `knead` | ✓ | ✓ | ✓ | yes |
| `proof` | ✓ | ✓ | single | yes |
| `roll` | ✓ | ✓ | single | yes |
| `shape` | ✓ | ✓ | single | yes |
| `grease` | ✓ | — | ✓ | no |
| `garnish` | ✓ | ✓ | ✓ | yes |
| `drizzle` | ✓ | ✓ | ✓ | yes |
| `glaze` | ✓ | ✓ | ✓ | yes |
| `dust` | ✓ | ✓ | ✓ | yes |
| `plate` | ✓ | ✓ | ✓ | yes |
| `serve` | ✓ | ✓ | ✓ | yes |
| `pickle` | ✓ | ✓ | ✓ | yes |
| `ferment` | ✓ | ✓ | ✓ | yes |
| `dry` | ✓ | ✓ | ✓ | yes |
| `cure` | ✓ | ✓ | ✓ | yes |
| `preserve` | ✓ | ✓ | ✓ | yes |
| `wait` | ✓ | ✓ | ✓ | no |
| `rest` | ✓ | ✓ | ✓ | yes |
| `chill` | ✓ | ✓ | ✓ | yes |
| `cool` | ✓ | ✓ | ✓ | yes |
| `freeze` | ✓ | ✓ | ✓ | yes |
| `thaw` | ✓ | ✓ | ✓ | yes |
| `custom` | ✓ | ✓ | ✓ | no |

Validator semantics: ingredients on a `PROCESS`/`NONE` action, subprocess references on an `INGREDIENT`/`NONE` action, more than
one ingredient on a single-ingredient action, and an empty Action On for an action with `actionOnRequired` are all rejected. The UI hides the
Ingredients or Processes tab when the selected action can't use it.

## H. AI prompt, validator and vocabulary changes

**`RecipeStepVocabularyProvider`** now parses the full schema into typed definitions: actions, ingredients, units, styles, sets, heat
levels, temperature units and contexts, and duration units. It adds lookups such as `allowedPreparationStyles(actionId)`, alias
resolution, `isQuantifiableUnit`, and unit and temperature labels. The existing `…Ids()` and `…Label()` methods remain, so
existing callers keep working.

**Prompt (`RecipeProcessGenerationPromptBuilder`)** renders the vocabulary from the provider. It does not maintain its own list:
- Actions are grouped by category, and each line gives the targets, the fields with required and recommended markers, the preparation style sets, the
  temperature context, and a one-line description.
- Ingredients are grouped by category as `id (defaultUnit) [up to 3 aliases]`.
- Units are grouped by category, with the non-numeric ones marked. Preparation style sets are listed once and referenced by name.
- Output shape additions: `customActionName`, `temperatureValue` (number), `temperatureUnit` (`C`/`F`), `repeatInterval`, and a nullable
  `quantity`. `unit` is now a catalog unit id rather than the 5-value enum.
- New rules:
  - Output canonical ids only. Aliases are only for reading the recipe.
  - Prefer the most specific action. Use `cook` only when the recipe names no technique, and `custom` only as a last resort, with a
    `customActionName`.
  - Respect each action's targets and fields.
  - Give each ingredient its own quantity, unit and style.
  - Use `to-taste` or `as-needed` with a null quantity.
  - Write duration as `"<number> <seconds|minutes|hours>"`. For a range, use the lower bound and keep the range in the description.

**Validator (`RecipeProcessGenerationValidator`)** adds checks for:
- the action's Action On targets, the multiple-ingredient rule, and the required-target rule
- step-level field applicability
- whether the preparation style is allowed for the action
- the catalog unit (legacy enum values are still accepted)
- a quantity when the action requires one, unless the unit is non-numeric
- a known heat level id (previously unchecked)
- temperature value + unit pairing
- duration and repeat interval format
- a `customActionName` when the action is custom

When an unknown id matches an alias, the error names the canonical id (e.g. *"use 'cilantro'"*).

**Frontend.** The catalog modules read categories, descriptions, aliases, rules and sets from the JSON. Id types became plain
`string`, because literal unions would duplicate the JSON. Changes:
- The Step panel:
  - offers a searchable, categorized action list with descriptions
  - shows only the Action On tabs and fields the action allows
  - adds per-ingredient unit selectors (the ingredient's own units first) and preparation style options filtered per ingredient
  - adds a numeric temperature + °C/°F input labelled with its context
  - adds a repeat interval input
- The ingredient picker searches aliases and supports a named custom ingredient.
- The generation converter keeps the AI's custom action and custom ingredient names.

**Follow-ups, not in this change:**
- Feed each action's `visualization` cue into the step-image prompt.
- Let the canvas display amounts in the viewer's preferred unit system.
- Add a catalog admin UI.
