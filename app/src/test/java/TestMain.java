/**
 * 主测试类 - 运行所有测试程序
 */
public class TestMain {
    
    public static void main(String[] args) {
        System.out.println("=============================================");
        System.out.println("  开始运行BigTwo游戏系统测试");
        System.out.println("=============================================");
        
        // 运行模型测试
        System.out.println("\n[1] 运行模型(Models)测试");
        try {
            ModelTest.main(args);
        } catch (Exception e) {
            System.err.println("模型测试异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 运行游戏系统测试
        System.out.println("\n[2] 运行游戏系统(Game System)测试");
        try {
            GameSystemTest.main(args);
        } catch (Exception e) {
            System.err.println("游戏系统测试异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 测试完成
        System.out.println("\n=============================================");
        System.out.println("  BigTwo游戏系统测试完成");
        System.out.println("=============================================");
    }
} 