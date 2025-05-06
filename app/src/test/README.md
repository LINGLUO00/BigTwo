# BigTwo 游戏测试程序

本目录包含用于测试 BigTwo 游戏模型和控制器的测试程序。

## 测试文件

1. `TestMain.java` - 主测试程序，运行所有测试
2. `ModelTest.java` - 专门测试模型类 (Card, CardPattern, Deck, PatternValidator)
3. `GameSystemTest.java` - 测试 Game 类和 GameController 基本功能

## 如何运行测试

### 方法一：使用 IDE

1. 在 Android Studio 或 IntelliJ IDEA 中打开项目
2. 找到 `app/src/test/java/TestMain.java` 文件
3. 右键点击并选择 "Run TestMain.main()"

### 方法二：使用命令行

```bash
# 编译测试类
javac -cp app/src/main/java app/src/test/java/*.java -d app/src/test/classes

# 运行测试主程序
java -cp app/src/main/java:app/src/test/classes TestMain
```

## 测试内容

测试程序会对以下内容进行验证：

1. **模型测试**
   - Card 类：创建卡牌、比较卡牌大小、选择卡牌
   - Deck 类：创建牌组、洗牌、发牌
   - CardPattern 和 PatternValidator：验证各种牌型 (单张、对子、三张、顺子、葫芦等)

2. **游戏系统测试**
   - Game 类：游戏状态、玩家管理、游戏流程
   - GameController：与 Game 模型的交互、网络游戏功能（基础测试）

## 注意事项

1. 这些测试是基本功能测试，主要用于验证模型的正确性
2. 对于 UI 交互和网络功能，可能需要在真实设备或模拟器上进行测试
3. 如果碰到异常，测试程序会捕获并显示错误信息，但会继续运行其他测试 