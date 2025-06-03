package models;

/**
 * 游戏工厂类，用于创建不同模式的游戏
 */
public class GameFactory {

    /**
     * 创建一个单机游戏（一个人类玩家，多个AI对手）
     * 
     * @param playerName 人类玩家名称
     * @param aiCount    AI玩家数量 (1-3)
     * @return 创建的游戏实例
     */
    public static Game createSinglePlayerGame(String playerName, int aiCount, AIStrategy aiStrategy) {
        // 验证AI玩家数量是否合法
        if (aiCount < 1 || aiCount > 3) {
            throw new IllegalArgumentException("AI玩家数量必须在1-3之间");
        }

        Game game = new Game();

        // 添加人类玩家
        Player humanPlayer = new Player(playerName, true);
        game.addPlayer(humanPlayer);

        // 添加AI玩家
        for (int i = 0; i < aiCount; i++) {
            AIPlayer aiPlayer = new AIPlayer("AI " + (i + 1), aiStrategy);
            game.addPlayer(aiPlayer);
        }

        return game;
    }

    /**
     * 创建一个多人游戏（多个人类玩家）
     * 
     * @param playerNames 所有人类玩家的名称
     * @return 创建的游戏实例
     */
    public static Game createMultiPlayerGame(String[] playerNames) {
        // 验证玩家数量是否合法
        if (playerNames.length < 2 || playerNames.length > 4) {
            throw new IllegalArgumentException("玩家数量必须在2-4之间");
        }

        Game game = new Game();

        // 添加所有人类玩家
        for (String name : playerNames) {
            Player player = new Player(name, true);
            game.addPlayer(player);
        }

        return game;
    }

    /**
     * 创建一个混合游戏（部分人类玩家，部分AI玩家）
     * 
     * @param playerNames 人类玩家名称
     * @param aiCount     AI玩家数量
     * @return 创建的游戏实例
     */
    public static Game createMixedGame(String[] playerNames, int aiCount) {
        // 验证总玩家数量是否合法
        if (playerNames.length + aiCount < 2 || playerNames.length + aiCount > 4) {
            throw new IllegalArgumentException("总玩家数量必须在2-4之间");
        }

        Game game = new Game();

        // 添加人类玩家
        for (String name : playerNames) {
            Player player = new Player(name, true);
            game.addPlayer(player);
        }

        // 添加AI玩家
        for (int i = 0; i < aiCount; i++) {
            AIStrategy strategy;

            // 第一个AI使用简单策略，其他使用高级策略
            if (i % 2 == 0) {
                strategy = new AdvancedAIStrategy();
            } else {
                strategy = new SmartAIStrategy();
            }

            AIPlayer aiPlayer = new AIPlayer("AI " + (i + 1), strategy);
            game.addPlayer(aiPlayer);
        }

        return game;
    }
}
