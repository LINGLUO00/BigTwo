package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 简单的游戏逻辑测试，不依赖JUnit
 */
public class GameSimpleTest {
    
    public static void main(String[] args) {
        testGameWithHumanAndAdvancedAI();
    }
    
    private static void testGameWithHumanAndAdvancedAI() {
        System.out.println("开始测试人类玩家与高级AI间的游戏...");
        
        // 创建游戏实例
        Game game = new Game();
        game.setAutoFillBots(false);
        
        // 创建玩家 - 让AI作为首家
        AIPlayer ai = new AIPlayer("高级AI", new AdvancedAIStrategy());
        Player human = new Player("人类玩家", true);
        
        // 添加玩家 - 先添加AI，确保它是首家
        game.addPlayer(ai);
        game.addPlayer(human);
        
        // 设置监听器
        game.addGameStateListener(new Game.GameStateListener() {
            @Override
            public void onGameStarted(Game g) {
                System.out.println("游戏开始！");
            }
            
            @Override
            public void onCardsPlayed(Game g, Player p, List<Card> cards, CardPattern pattern) {
                System.out.println(p.getName() + " 打出了: " + cards + " (" + pattern.getPatternTypeDisplayName(pattern.getType()) + ")");
            }
            
            @Override
            public void onPlayerPassed(Game g, Player p) {
                System.out.println(p.getName() + " 选择不出");
            }
            
            @Override
            public void onGameOver(Game g, Player winner) {
                System.out.println("游戏结束！胜利者: " + winner.getName());
            }
        });
        
        // 开始游戏
        game.startGame();
        System.out.println("游戏状态: " + game.getState());
        
        // 进行游戏，直到游戏结束
        Scanner scanner = new Scanner(System.in);
        
        while (game.getState() == Game.State.PLAYING) {
            Player currentPlayer = game.getCurrentPlayer();
            System.out.println("\n当前玩家: " + currentPlayer.getName());
            System.out.println(currentPlayer.getName() + " 的手牌: " + currentPlayer.getHand());
            
            if (currentPlayer.isHuman()) {
                // 人类玩家回合
                boolean validMove = false;
                
                while (!validMove) {
                    System.out.println("请选择要出的牌的索引（从0开始，用空格分隔），或输入p跳过:");
                    String input = scanner.nextLine().trim();
                    
                    if (input.equalsIgnoreCase("p")) {
                        validMove = game.pass();
                        if (!validMove) {
                            System.out.println("无法跳过，当前为自由出牌轮次!");
                        }
                    } else {
                        try {
                            // 清除之前的选择
                            currentPlayer.clearSelections();
                            
                            // 解析输入的索引
                            String[] indices = input.split("\\s+");
                            for (String idx : indices) {
                                int cardIndex = Integer.parseInt(idx);
                                currentPlayer.toggleCardSelection(cardIndex);
                            }
                            
                            // 尝试出牌
                            validMove = game.playSelected();
                            if (!validMove) {
                                System.out.println("无效的出牌！请重新选择。");
                            }
                        } catch (NumberFormatException | IndexOutOfBoundsException e) {
                            System.out.println("输入无效，请输入有效的索引！");
                        }
                    }
                }
            } else if (currentPlayer instanceof AIPlayer) {
                // AI玩家回合
                AIPlayer aiPlayer = (AIPlayer) currentPlayer;
                
                // 获取其他玩家的手牌数量
                List<Integer> othersCount = new ArrayList<>();
                for (Player p : game.getPlayers()) {
                    if (p != currentPlayer) {
                        othersCount.add(p.getHand().size());
                    }
                }
                
                // AI做出决策
                List<Card> decision = aiPlayer.makeDecision(game.getLastPattern(), othersCount);
                
                // 如果有选择的牌则出牌，否则跳过
                if (decision.isEmpty()) {
                    boolean passed = game.pass();
                    if (!passed) {
                        System.out.println("AI无法跳过，当前为自由出牌轮次!");
                        // 在首轮特殊处理，确保AI能够出方片3
                        if (game.getLastPattern() == null) {
                            // 尝试强制AI出方片3
                            currentPlayer.clearSelections();
                            for (int i = 0; i < currentPlayer.getHand().size(); i++) {
                                Card card = currentPlayer.getHand().get(i);
                                if (card.getSuit() == Card.DIAMOND && card.getRank() == Card.THREE) {
                                    currentPlayer.toggleCardSelection(i);
                                    break;
                                }
                            }
                            boolean success = game.playSelected();
                            if (!success) {
                                System.out.println("AI出牌强制方片3失败！");
                            }
                        }
                    }
                } else {
                    boolean success = game.playSelected();
                    if (!success) {
                        System.out.println("AI出牌失败！可能是策略错误。");
                        // 在首轮特殊处理，确保AI能够出方片3
                        if (game.getLastPattern() == null) {
                            // 尝试强制AI出方片3
                            currentPlayer.clearSelections();
                            for (int i = 0; i < currentPlayer.getHand().size(); i++) {
                                Card card = currentPlayer.getHand().get(i);
                                if (card.getSuit() == Card.DIAMOND && card.getRank() == Card.THREE) {
                                    currentPlayer.toggleCardSelection(i);
                                    break;
                                }
                            }
                            success = game.playSelected();
                            if (!success) {
                                System.out.println("AI出牌强制方片3失败！");
                            }
                        } else {
                            game.pass();
                        }
                    }
                }
                
                // 暂停一下，便于观察AI的行为
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        scanner.close();
        System.out.println("测试完成！");
    }
}