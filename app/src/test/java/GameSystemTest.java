import models.*;
import controller.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏系统测试类 - 用于测试模型和控制器的正确工作
 */
public class GameSystemTest {
    
    // 测试View实现，用于捕获GameController的输出
    private static class TestGameView implements GameView {
        public Game lastGameState;
        public Player lastPlayerHandUpdate;
        public List<String> messages = new ArrayList<>();
        
        @Override
        public void updateGameState(Game game) {
            lastGameState = game;
            messages.add("游戏状态更新: " + game.getState());
        }
        
        @Override
        public void updatePlayerHand(Player player) {
            lastPlayerHandUpdate = player;
            messages.add("玩家手牌更新: " + player.getName() + ", 牌数:" + player.getHand().size());
        }
    }
    
    public static void main(String[] args) {
        testGameModel();
        testGameControllerBasic();
    }
    
    /**
     * 测试Game模型
     */
    private static void testGameModel() {
        System.out.println("=== 测试Game模型 ===");
        
        // 创建游戏实例
        Game game = new Game();
        
        // 添加测试监听器
        game.addListener(new Game.GameStateListener() {
            @Override
            public void onGameStarted(Game g) {
                System.out.println("游戏开始事件: 玩家数=" + g.getPlayers().size());
            }
            
            @Override
            public void onCardsPlayed(Game g, Player p, List<Card> cards, CardPattern pattern) {
                System.out.println("出牌事件: 玩家=" + p.getName() + 
                                   ", 牌型=" + pattern.getPatternType() + 
                                   ", 牌数=" + cards.size());
            }
            
            @Override
            public void onPlayerPassed(Game g, Player p) {
                System.out.println("玩家Pass事件: 玩家=" + p.getName());
            }
            
            @Override
            public void onGameOver(Game g, Player winner) {
                System.out.println("游戏结束事件: 获胜者=" + winner.getName());
            }
        });
        
        // 添加玩家
        game.addPlayer(new Player("测试玩家", true));
        
        // 启动游戏 (会自动添加AI玩家)
        game.startGame();
        
        // 验证游戏状态
        System.out.println("游戏状态: " + game.getState());
        System.out.println("玩家数量: " + game.getPlayers().size());
        System.out.println("当前玩家: " + game.getCurrentPlayer().getName());
        
        // 打印玩家手牌
        for (Player p : game.getPlayers()) {
            System.out.println(p.getName() + " 手牌: " + p.getHand().size() + "张");
        }
        
        System.out.println("Game模型测试完成\n");
    }
    
    /**
     * 测试基本的GameController功能
     * 注意: 由于GameController可能与AndroidUI绑定,
     * 这里只测试基本功能
     */
    private static void testGameControllerBasic() {
        System.out.println("=== 测试GameController基本功能 ===");
        
        try {
            // 创建测试视图
            TestGameView testView = new TestGameView();
            
            // 创建控制器
            GameController controller = new GameController(testView);
            System.out.println("GameController创建成功");
            
            // 获取游戏实例
            Game game = controller.getGame();
            if (game == null) {
                System.out.println("提示: 需要先调用createSinglePlayerGame或createNetworkGame来初始化游戏");
            }
            
            // 检查控制器的单机游戏工厂方法
            GameFactory factory = new GameFactory();
            Game testGame = factory.createSinglePlayerGame("测试玩家", 3);
            if (testGame != null) {
                System.out.println("游戏工厂方法测试: 创建成功");
                System.out.println("- 玩家数量: " + testGame.getPlayers().size());
                System.out.println("- 状态: " + testGame.getState());
            }
        } catch (Exception e) {
            System.out.println("GameController测试异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("GameController基本测试完成");
    }
    
    /**
     * 提供一种更复杂的测试方法,模拟完整游戏流程
     */
    private static void simulateGamePlay(Game game) {
        System.out.println("=== 模拟游戏流程 ===");
        
        // 获取人类玩家
        Player humanPlayer = null;
        for (Player p : game.getPlayers()) {
            if (p.isHuman()) {
                humanPlayer = p;
                break;
            }
        }
        
        if (humanPlayer == null) {
            System.out.println("没有找到人类玩家");
            return;
        }
        
        // 模拟几轮出牌
        System.out.println("模拟人类玩家选牌和出牌:");
        
        // 选择并出第一张牌 (模拟)
        List<Card> hand = humanPlayer.getHand();
        if (!hand.isEmpty()) {
            // 模拟选牌过程
            Card firstCard = hand.get(0);
            System.out.println("选择牌: " + firstCard);
            humanPlayer.selectCard(firstCard);
            
            // 尝试出牌
            boolean success = game.playSelected();
            System.out.println("出牌结果: " + (success ? "成功" : "失败"));
        }
        
        // 模拟PASS
        boolean passResult = game.pass();
        System.out.println("PASS结果: " + (passResult ? "成功" : "失败"));
        
        System.out.println("游戏流程模拟完成");
    }
} 