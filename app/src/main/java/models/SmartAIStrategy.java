package models;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class SmartAIStrategy implements AIStrategy {
    private PatternValidator validator;

    public SmartAIStrategy() {
        this.validator = new PatternValidatorImpl();
    }

    /**
     * 核心AI决策方法，模拟搜索来选择最佳出牌策略
     */
    @Override
    public List<Card> makeMove(List<Card> hand, CardPattern lastPattern, List<Integer> othersCardCount) {
        if (hand == null || hand.isEmpty()) {
            return new ArrayList<>();
        }

        // 先对手牌排序
        List<Card> sortedHand = new ArrayList<>(hand);
        Collections.sort(sortedHand);

        // 如果是首轮出牌（没有上一个牌型），AI会尽量出最小的牌（包含方块3）
        if (lastPattern == null) {
            return playFirstMove(sortedHand);
        } else {
            // 如果有上一个牌型，AI会尝试模拟不同的出牌组合来判断最优解
            return findBestMove(sortedHand, lastPattern, othersCardCount);
        }
    }

    /**
     * 首轮出牌逻辑，确保包含方块3
     */
    private List<Card> playFirstMove(List<Card> sortedHand) {
        Card diamondThree = findDiamondThree(sortedHand);
        
        // 如果有方块3，优先出方块3
        if (diamondThree != null) {
            List<Card> result = new ArrayList<>();
            result.add(diamondThree);
            return result;
        }

        // 如果没有方块3，则出最小的一张牌
        return sortedHand.subList(0, 1);
    }

    /**
     * 寻找方块3
     */
    private Card findDiamondThree(List<Card> sortedHand) {
        for (Card card : sortedHand) {
            if (card.getSuit() == Card.DIAMOND && card.getRank() == Card.THREE) {
                return card;
            }
        }
        return null;
    }

    /**
     * 查找最优出牌（通过模拟搜索）
     */
    private List<Card> findBestMove(List<Card> sortedHand, CardPattern lastPattern, List<Integer> othersCardCount) {
        // 模拟搜索：试图出多种牌型，评估每种可能的效果，并选择最佳的出牌策略
        List<List<Card>> possiblePlays = generatePossiblePlays(sortedHand);
        
        List<Card> bestPlay = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;

        // 遍历每种可能的出牌，并进行模拟评分
        for (List<Card> play : possiblePlays) {
            int score = evaluateMove(play, lastPattern, othersCardCount);
            if (score > bestScore) {
                bestScore = score;
                bestPlay = play;
            }
        }

        return bestPlay;
    }

    /**
     * 生成所有可能的出牌组合
     */
    private List<List<Card>> generatePossiblePlays(List<Card> sortedHand) {
        List<List<Card>> plays = new ArrayList<>();

        // 生成单牌、对子等所有合法的牌型组合
        for (int i = 0; i < sortedHand.size(); i++) {
            List<Card> singlePlay = new ArrayList<>();
            singlePlay.add(sortedHand.get(i));
            plays.add(singlePlay);

            for (int j = i + 1; j < sortedHand.size(); j++) {
                if (sortedHand.get(i).getRank() == sortedHand.get(j).getRank()) {
                    List<Card> pair = new ArrayList<>();
                    pair.add(sortedHand.get(i));
                    pair.add(sortedHand.get(j));
                    plays.add(pair);
                }
            }
        }

        // 根据需求，可以扩展为生成更多的牌型，如顺子、三条等
        return plays;
    }

    /**
     * 评估一手牌的得分
     * 评分逻辑：更低的得分表示更强的出牌
     */
    private int evaluateMove(List<Card> play, CardPattern lastPattern, List<Integer> othersCardCount) {
        int score = 0;

        // 如果能击败上一手牌，增加得分
        if (lastPattern != null) {
            CardPattern playPattern = validator.validate(play);
            if (playPattern.canBeat(lastPattern)) {
                score += 10;  // 能打过上一手牌，增加10分
            }
        }

        // 考虑出牌后手牌数量，剩余的牌越少，得分越高
        score += play.size();  // 出牌越多，得分越低

        // 可以根据其他玩家的手牌数量来加权得分（例如，优先出牌时，使其他玩家处于劣势）
        for (int count : othersCardCount) {
            score -= count;  // 减少其他玩家手牌数的影响
        }

        return score;
    }

    @Override
    public String getName() {
        return "智能AI";
    }
}
