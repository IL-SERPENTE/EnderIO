package crazypants.enderio.base.config.config;

import crazypants.enderio.base.config.factory.IValue;
import crazypants.enderio.base.config.factory.ValueFactory;

public final class RecipeConfig {

  public static final ValueFactory F = BaseConfig.F.section("recipe");
  public static final ValueFactory FA = F.section(".alloy");
  public static final ValueFactory FP = F.section(".painter");

  public static final IValue<Integer> energyPerTask = FP.make("energyPerTask", 2000, //
      "The total amount of energy required to paint one block.").sync();

  public static final IValue<Boolean> allowTileEntitiesAsPaintSource = FP.make("allowTileEntitiesAsPaintSource", true, //
      "When enabled blocks with tile entities (e.g. machines) can be used as paint targets.").sync();

  public static final IValue<Boolean> createSyntheticRecipes = FA.make("createSyntheticRecipes", true, //
      "Automatically create alloy smelter recipes with double and triple inputs and different slot allocations (1+1+1, 2+1, 1+2, 3 and 2) for single-input recipes.")
      .sync();

  public static final IValue<Boolean> loadCoreRecipes = F.make("loadCoreRecipes", true, //
      "When disabled the XML recipe files that come built-in with Ender IO will not be loaded. Only user supplied files (in the 'recipes/user' folder) will be loaded.")
      .sync();

}
