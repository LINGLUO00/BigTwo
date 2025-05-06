import models.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型层测试类 - 测试Card、CardPattern、Deck和PatternValidator等核心模型
 */
public class ModelTest {
    
    public static void main(String[] args) {
        testCardBasics();
        testDeck();
        testPatternValidator();
    }
    
    /**
     * 测试Card类基本功能
     */
    private static void testCardBasics() {
        System.out.println("=== 测试Card类 ===");
        
        // 创建不同的卡牌
        Card diamondThree = new Card(Card.DIAMOND, Card.THREE);
        Card heartAce = new Card(Card.HEART, Card.ACE);
        Card spadeKing = new Card(Card.SPADE, Card.KING);
        
        // 验证卡牌属性
        System.out.println("方块3: " + diamondThree);
        System.out.println("红桃A: " + heartAce);
        System.out.println("黑桃K: " + spadeKing);
        
        // 验证比较功能
        System.out.println("方块3 < 红桃A: " + (diamondThree.compareTo(heartAce) < 0));
        System.out.println("红桃A > 黑桃K: " + (heartAce.compareTo(spadeKing) > 0));
        
        // 测试选择功能
        diamondThree.setSelected(true);
        System.out.println("方块3被选中: " + diamondThree.isSelected());
        
        System.out.println("Card类测试完成\n");
    }
    
    /**
     * 测试Deck类
     */
    private static void testDeck() {
        System.out.println("=== 测试Deck类 ===");
        
        // 创建新牌组
        Deck deck = new Deck();
        
        // 验证初始状态
        System.out.println("新牌组大小: " + deck.size());
        
        // 测试洗牌
        deck.shuffle(123); // 使用固定种子便于复现
        
        // 测试发牌
        List<List<Card>> hands = deck.dealCards(4);
        
        // 验证发牌结果
        System.out.println("发给4位玩家后的结果:");
        for (int i = 0; i < hands.size(); i++) {
            List<Card> hand = hands.get(i);
            System.out.println("玩家" + (i+1) + " 手牌数: " + hand.size());
            // 打印前3张牌作为示例
            for (int j = 0; j < Math.min(3, hand.size()); j++) {
                System.out.println("  - " + hand.get(j));
            }
            if (hand.size() > 3) {
                System.out.println("  - ...(省略其余牌)");
            }
        }
        
        System.out.println("Deck类测试完成\n");
    }
    
    /**
     * 测试PatternValidator
     */
    private static void testPatternValidator() {
        System.out.println("=== 测试牌型验证 ===");
        
        PatternValidator validator = new PatternValidatorImpl();
        
        // 测试各种牌型
        testSingleCard(validator);
        testPair(validator);
        testThreeOfKind(validator);
        testStraight(validator);
        testFullHouse(validator);
        
        System.out.println("牌型验证测试完成\n");
    }
    
    /**
     * 测试单张
     */
    private static void testSingleCard(PatternValidator validator) {
        Card card = new Card(Card.HEART, Card.ACE);
        List<Card> cards = new ArrayList<>();
        cards.add(card);
        
        CardPattern pattern = validator.validate(cards);
        System.out.println("单张 [红桃A] 验证结果: " + 
                           getPatternTypeName(pattern.getPatternType()) + 
                           ", 大小: " + pattern.getValue());
    }
    
    /**
     * 测试对子
     */
    private static void testPair(PatternValidator validator) {
        List<Card> cards = new ArrayList<>();
        cards.add(new Card(Card.HEART, Card.TEN));
        cards.add(new Card(Card.DIAMOND, Card.TEN));
        
        CardPattern pattern = validator.validate(cards);
        System.out.println("对子 [红桃10, 方块10] 验证结果: " + 
                           getPatternTypeName(pattern.getPatternType()) + 
                           ", 大小: " + pattern.getValue());
        
        // 无效对子
        cards.clear();
        cards.add(new Card(Card.HEART, Card.TEN));
        cards.add(new Card(Card.DIAMOND, Card.NINE));
        
        pattern = validator.validate(cards);
        System.out.println("无效对子 [红桃10, 方块9] 验证结果: " + 
                           getPatternTypeName(pattern.getPatternType()));
    }
    
    /**
     * 测试三张
     */
    private static void testThreeOfKind(PatternValidator validator) {
        List<Card> cards = new ArrayList<>();
        cards.add(new Card(Card.HEART, Card.SEVEN));
        cards.add(new Card(Card.DIAMOND, Card.SEVEN));
        cards.add(new Card(Card.SPADE, Card.SEVEN));
        
        CardPattern pattern = validator.validate(cards);
        System.out.println("三张 [三张7] 验证结果: " + 
                           getPatternTypeName(pattern.getPatternType()) + 
                           ", 大小: " + pattern.getValue());
    }
    
    /**
     * 测试顺子
     */
    private static void testStraight(PatternValidator validator) {
        List<Card> cards = new ArrayList<>();
        cards.add(new Card(Card.HEART, Card.THREE));
        cards.add(new Card(Card.DIAMOND, Card.FOUR));
        cards.add(new Card(Card.SPADE, Card.FIVE));
        cards.add(new Card(Card.CLUB, Card.SIX));
        cards.add(new Card(Card.HEART, Card.SEVEN));
        
        CardPattern pattern = validator.validate(cards);
        System.out.println("顺子 [3,4,5,6,7] 验证结果: " + 
                           getPatternTypeName(pattern.getPatternType()) + 
                           ", 大小: " + pattern.getValue());
    }
    
    /**
     * 测试葫芦
     */
    private static void testFullHouse(PatternValidator validator) {
        List<Card> cards = new ArrayList<>();
        cards.add(new Card(Card.HEART, Card.FIVE));
        cards.add(new Card(Card.DIAMOND, Card.FIVE));
        cards.add(new Card(Card.SPADE, Card.FIVE));
        cards.add(new Card(Card.CLUB, Card.NINE));
        cards.add(new Card(Card.HEART, Card.NINE));
        
        CardPattern pattern = validator.validate(cards);
        System.out.println("葫芦 [三张5,两张9] 验证结果: " + 
                           getPatternTypeName(pattern.getPatternType()) + 
                           ", 大小: " + pattern.getValue());
    }
    
    /**
     * 获取牌型名称
     */
    private static String getPatternTypeName(int patternType) {
        switch (patternType) {
            case CardPattern.SINGLE: return "单张";
            case CardPattern.PAIR: return "对子";
            case CardPattern.THREE_OF_A_KIND: return "三张";
            case CardPattern.STRAIGHT: return "顺子";
            case CardPattern.FLUSH: return "同花";
            case CardPattern.FULL_HOUSE: return "葫芦";
            case CardPattern.FOUR_OF_A_KIND: return "四张";
            case CardPattern.STRAIGHT_FLUSH: return "同花顺";
            case CardPattern.INVALID: return "无效牌型";
            default: return "未知牌型";
        }
    }
} 