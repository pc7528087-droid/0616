import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

enum Team { ALLY, ENEMY }
enum BuildingType { TOWER, NEXUS } 
enum Lane { TOP, MID, BOT, NONE } 

// ==========================================
// 1. 遊戲主程式啟動點
// ==========================================
public class MobaGame {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Java LoL - 防禦塔護盾與強化小兵");
        GamePanel panel = new GamePanel();
        frame.add(panel);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        panel.startGame();
    }
}

// ==========================================
// 2. 基礎遊戲物件
// ==========================================
abstract class GameObject {
    protected double x, y;
    protected double speed;
    public Team team;
    public Lane lane; 
    public boolean active = true; 
    public boolean isDead = false;

    public double hp, maxHp;
    public double atk;
    public double attackRange;
    public int attackCooldown = 0;
    public int maxCooldown = 60; 

    public GameObject(double x, double y, double speed, Team team, double maxHp, double atk, double range, Lane lane) {
        this.x = x; this.y = y; this.speed = speed; this.team = team;
        this.maxHp = maxHp; this.hp = maxHp;
        this.atk = atk; this.attackRange = range;
        this.lane = lane;
    }

    public abstract void update(GamePanel panel, List<GameObject> enemies);
    public abstract void draw(Graphics g);

    public void takeDamage(double amount) {
        hp -= amount;
    }

    public void die() {
        active = false;
    }

    public double distanceTo(GameObject other) {
        return Math.hypot(this.x - other.x, this.y - other.y);
    }

    public GameObject findClosestEnemy(List<GameObject> enemies, double range) {
        GameObject closest = null;
        double minDst = range;
        for (GameObject enemy : enemies) {
            if (!enemy.active || enemy.isDead || enemy.hp <= 0) continue;
            
            // 【新增】如果目標是防禦塔且處於保護狀態(附近無我方小兵)，則無法鎖定
            if (enemy instanceof Tower && ((Tower) enemy).isProtected) {
                continue; 
            }

            // 檢查路線限制，但將 Player 排除在外
            if (this.lane != Lane.NONE && enemy.lane != Lane.NONE && this.lane != enemy.lane) {
                if (!(this instanceof Player) && !(enemy instanceof Player)) {
                    continue; 
                }
            }

            double dst = this.distanceTo(enemy);
            if (dst <= minDst) {
                minDst = dst; closest = enemy;
            }
        }
        return closest;
    }

    public void drawHpBar(Graphics g, int yOffset, int width) {
        if (isDead) return;
        int barX = (int)x - width / 2;
        int barY = (int)y - yOffset;
        g.setColor(Color.BLACK);
        g.fillRect(barX, barY, width, 5); 
        g.setColor(team == Team.ALLY ? Color.GREEN : Color.RED);
        double hpPercent = Math.max(0, hp / maxHp);
        g.fillRect(barX, barY, (int)(width * hpPercent), 5); 
    }
}

// ==========================================
// 3. 建築物系統
// ==========================================
class Tower extends GameObject {
    public BuildingType type; 
    public boolean isProtected = true; // 【新增】防禦塔保護狀態

    public Tower(double x, double y, Team team, BuildingType type, Lane lane) {
        super(x, y, 0, team, 
              type == BuildingType.NEXUS ? 400 : 200, 
              type == BuildingType.NEXUS ? 0 : 20,   
              150, lane);                                    
        this.type = type;
    }

    @Override
    public void update(GamePanel panel, List<GameObject> enemies) {
        if (atk == 0 || isDead) return; 
        if (attackCooldown > 0) attackCooldown--;

        GameObject target = findClosestEnemy(enemies, attackRange);
        if (target != null && attackCooldown <= 0) {
            target.takeDamage(atk); 
            attackCooldown = maxCooldown;
        }
    }

    @Override
    public void draw(Graphics g) {
        if (isDead) return;
        
        // 【新增】如果防禦塔受到保護，畫出金色護盾
        if (isProtected) {
            g.setColor(new Color(255, 215, 0, 100)); // 金色半透明
            if (type == BuildingType.NEXUS) {
                g.fillOval((int)x - 35, (int)y - 35, 70, 70); 
            } else {
                g.fillOval((int)x - 30, (int)y - 30, 60, 60); 
            }
        }

        g.setColor(team == Team.ALLY ? new Color(0, 0, 139) : new Color(139, 0, 0));
        if (type == BuildingType.NEXUS) {
            g.fillRect((int)x - 25, (int)y - 25, 50, 50); 
            drawHpBar(g, 35, 40);
        } else {
            g.fillOval((int)x - 20, (int)y - 20, 40, 40); 
            drawHpBar(g, 30, 40);
        }
    }
}

// ==========================================
// 4. 小兵系統
// ==========================================
class Minion extends GameObject {
    private Point[] path; 
    private int pathIndex = 0; 

    public Minion(double x, double y, Point[] path, Team team, Lane lane) {
        // 【修改】將小兵的血量從 50 提升到 150
        super(x, y, 1.5, team, 150, 10, 40, lane); 
        this.path = path;
    }

    @Override
    public void update(GamePanel panel, List<GameObject> enemies) {
        if (isDead) return;
        if (attackCooldown > 0) attackCooldown--;
        GameObject target = findClosestEnemy(enemies, attackRange);

        if (target == null && pathIndex < path.length) {
            double targetX = path[pathIndex].x;
            double targetY = path[pathIndex].y;
            double dx = targetX - x;
            double dy = targetY - y;
            double distance = Math.hypot(dx, dy);

            if (distance > speed) {
                x += (dx / distance) * speed;
                y += (dy / distance) * speed;
            } else {
                x = targetX; y = targetY; pathIndex++;
            }
            target = findClosestEnemy(enemies, attackRange);
        }

        if (target != null && attackCooldown <= 0) {
            target.takeDamage(atk); 
            attackCooldown = maxCooldown;
        }
    }

    @Override
    public void draw(Graphics g) {
        if (isDead) return;
        g.setColor(team == Team.ALLY ? Color.CYAN : Color.ORANGE);
        g.fillOval((int)x - 6, (int)y - 6, 12, 12);
        drawHpBar(g, 12, 20); 
    }
}

// ==========================================
// 5. 玩家英雄
// ==========================================
class Player extends GameObject {
    private double targetX, targetY;
    private double spawnX, spawnY;
    public int respawnTimer = 0;   
    
    public int qCooldown = 0;
    public int wCooldown = 0;
    public int eCooldown = 0;
    public int rCooldown = 0;

    public Player(double x, double y, Team team, Lane lane) {
        super(x, y, 3.0, team, 500, 0, 0, lane); 
        this.spawnX = x; 
        this.spawnY = y;
        this.targetX = x; 
        this.targetY = y;
    }

    public void setTarget(double x, double y) {
        if (!isDead) {
            this.targetX = x; this.targetY = y;
        }
    }

    @Override
    public void die() {
        isDead = true;
        hp = 0;
        respawnTimer = 600; 
        x = spawnX;
        y = spawnY;
        targetX = spawnX;
        targetY = spawnY;
    }

    @Override
    public void update(GamePanel panel, List<GameObject> enemies) {
        if (isDead) {
            respawnTimer--;
            if (respawnTimer <= 0) {
                isDead = false;
                hp = maxHp;
                qCooldown = wCooldown = eCooldown = rCooldown = 0; 
            }
            return;
        }

        if (qCooldown > 0) qCooldown--;
        if (wCooldown > 0) wCooldown--;
        if (eCooldown > 0) eCooldown--;
        if (rCooldown > 0) rCooldown--;

        double dx = targetX - x;
        double dy = targetY - y;
        double distance = Math.hypot(dx, dy);
        if (distance > speed) {
            x += (dx / distance) * speed;
            y += (dy / distance) * speed;
        } else {
            x = targetX; y = targetY;
        }
    }

    @Override
    public void draw(Graphics g) {
        if (!active || isDead) return; 
        g.setColor(Color.BLUE);
        g.fillRect((int)x - 12, (int)y - 12, 24, 24); 
        drawHpBar(g, 20, 30); 
        
        g.setColor(Color.WHITE);
        g.drawString("P", (int)x - 4, (int)y + 5);
    }
}

// ==========================================
// 6. AI 英雄
// ==========================================
class BotPlayer extends GameObject {
    private Point[] path; 
    private int pathIndex = 0;
    private int skillCooldown = 0;
    private final int MAX_SKILL_COOLDOWN = 90; 
    
    private double spawnX, spawnY;
    public int respawnTimer = 0;   

    public BotPlayer(double x, double y, Team team, Point[] path, Lane lane) {
        super(x, y, 2.5, team, 500, 0, 250, lane); 
        this.path = path;
        this.spawnX = x;
        this.spawnY = y;
    }

    @Override
    public void die() {
        isDead = true;
        hp = 0;
        respawnTimer = 600; 
        x = spawnX; 
        y = spawnY;
        pathIndex = 0;
    }

    @Override
    public void update(GamePanel panel, List<GameObject> enemies) {
        if (isDead) {
            respawnTimer--;
            if (respawnTimer <= 0) {
                isDead = false;
                hp = maxHp;
            }
            return;
        }

        if (skillCooldown > 0) skillCooldown--;
        GameObject target = findClosestEnemy(enemies, attackRange);

        if (target != null) {
            if (skillCooldown <= 0) {
                panel.addProjectile(new Projectile(x, y, target.x, target.y, team, lane));
                skillCooldown = MAX_SKILL_COOLDOWN;
            }
        } else {
            if (pathIndex < path.length) {
                double targetX = path[pathIndex].x;
                double targetY = path[pathIndex].y;
                double dx = targetX - x;
                double dy = targetY - y;
                double distance = Math.hypot(dx, dy);

                if (distance > speed) {
                    x += (dx / distance) * speed;
                    y += (dy / distance) * speed;
                } else {
                    x = targetX; y = targetY; pathIndex++;
                }
            }
        }
    }

    @Override
    public void draw(Graphics g) {
        if (!active || isDead) return;
        g.setColor(team == Team.ALLY ? Color.BLUE : Color.RED);
        g.fillRect((int)x - 12, (int)y - 12, 24, 24); 
        drawHpBar(g, 20, 30);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.drawString("AI", (int)x - 5, (int)y + 4);
    }
}

// ==========================================
// 7. 自訂義技能投射物
// ==========================================
class Projectile {
    public double x, y, vx, vy, speed, damage; 
    public int size;
    public Color color;
    public Team team;
    public Lane lane; 
    public boolean active = true;

    public Projectile(double startX, double startY, double targetX, double targetY, Team team, Lane lane) {
        this(startX, startY, targetX, targetY, team, lane, 50.0, 8.0, 10, team == Team.ALLY ? Color.CYAN : Color.ORANGE);
    }

    public Projectile(double startX, double startY, double targetX, double targetY, Team team, Lane lane, double damage, double speed, int size, Color color) {
        this.x = startX; this.y = startY; this.team = team; this.lane = lane;
        this.damage = damage; this.speed = speed; this.size = size; this.color = color;
        double distance = Math.hypot(targetX - startX, targetY - startY);
        if (distance > 0) {
            vx = ((targetX - startX) / distance) * speed;
            vy = ((targetY - startY) / distance) * speed;
        }
    }

    public void update() {
        x += vx; y += vy;
        if (x < 0 || x > 800 || y < 0 || y > 600) active = false;
    }

    public void draw(Graphics g) {
        g.setColor(color);
        g.fillOval((int)x - size / 2, (int)y - size / 2, size, size);
    }
}

// ==========================================
// 8. 遊戲引擎與控制器
// ==========================================
class GamePanel extends JPanel implements Runnable {
    private Thread gameThread;
    private Player player;
    private List<GameObject> allEntities; 
    private List<Projectile> projectiles; 
    private int frameCount = 0;

    private int mouseX = 0, mouseY = 0;
    private boolean gameOver = false;
    private String endMessage = "";

    public GamePanel() {
        setPreferredSize(new Dimension(800, 600));
        setBackground(new Color(34, 139, 34));
        setFocusable(true); 
        
        allEntities = new ArrayList<>();
        projectiles = new ArrayList<>();

        allEntities.add(new Tower(100, 500, Team.ALLY, BuildingType.NEXUS, Lane.NONE));
        allEntities.add(new Tower(700, 100, Team.ENEMY, BuildingType.NEXUS, Lane.NONE));
        allEntities.add(new Tower(100, 300, Team.ALLY, BuildingType.TOWER, Lane.TOP)); 
        allEntities.add(new Tower(300, 500, Team.ALLY, BuildingType.TOWER, Lane.BOT)); 
        allEntities.add(new Tower(250, 400, Team.ALLY, BuildingType.TOWER, Lane.MID)); 
        allEntities.add(new Tower(500, 100, Team.ENEMY, BuildingType.TOWER, Lane.TOP)); 
        allEntities.add(new Tower(700, 300, Team.ENEMY, BuildingType.TOWER, Lane.BOT)); 
        allEntities.add(new Tower(550, 200, Team.ENEMY, BuildingType.TOWER, Lane.MID)); 

        Point[] allyTop = {new Point(100, 100), new Point(700, 100)};
        Point[] allyBot = {new Point(700, 500), new Point(700, 100)};
        Point[] enemyTop = {new Point(100, 100), new Point(100, 500)};
        Point[] enemyMid = {new Point(100, 500)};
        Point[] enemyBot = {new Point(700, 500), new Point(100, 500)};

        player = new Player(150, 450, Team.ALLY, Lane.MID);
        allEntities.add(player);

        allEntities.add(new BotPlayer(100, 500, Team.ALLY, allyTop, Lane.TOP)); 
        allEntities.add(new BotPlayer(100, 500, Team.ALLY, allyBot, Lane.BOT)); 
        allEntities.add(new BotPlayer(700, 100, Team.ENEMY, enemyTop, Lane.TOP)); 
        allEntities.add(new BotPlayer(700, 100, Team.ENEMY, enemyMid, Lane.MID)); 
        allEntities.add(new BotPlayer(700, 100, Team.ENEMY, enemyBot, Lane.BOT)); 

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { 
                if(player.active && !player.isDead && !gameOver) player.setTarget(e.getX(), e.getY()); 
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
        });
        
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!player.active || player.isDead || gameOver) return;

                if (e.getKeyCode() == KeyEvent.VK_Q && player.qCooldown <= 0) {
                    addProjectile(new Projectile(player.x, player.y, mouseX, mouseY, player.team, Lane.NONE));
                    player.qCooldown = 90; 
                }
                if (e.getKeyCode() == KeyEvent.VK_W && player.wCooldown <= 0) {
                    player.hp = Math.min(player.maxHp, player.hp + 150);
                    player.wCooldown = 300; 
                }
                if (e.getKeyCode() == KeyEvent.VK_E && player.eCooldown <= 0) {
                    addProjectile(new Projectile(player.x, player.y, mouseX, mouseY, player.team, Lane.NONE, 100.0, 12.0, 14, Color.YELLOW));
                    player.eCooldown = 240; 
                }
                if (e.getKeyCode() == KeyEvent.VK_R && player.rCooldown <= 0) {
                    addProjectile(new Projectile(player.x, player.y, mouseX, mouseY, player.team, Lane.NONE, 300.0, 6.0, 30, Color.MAGENTA));
                    player.rCooldown = 600; 
                }
            }
        });
    }

    public void addProjectile(Projectile p) { projectiles.add(p); }

    public void startGame() {
        gameThread = new Thread(this);
        gameThread.start();
        requestFocusInWindow(); 
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        double nsPerTick = 1000000000D / 60D;
        double delta = 0;
        while (true) {
            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerTick;
            lastTime = now;
            while (delta >= 1) { updateGameLogic(); delta--; }
            repaint();
        }
    }

    private void spawnMinionWave() {
        Point[] allyTop = {new Point(100, 100), new Point(700, 100)};
        Point[] allyMid = {new Point(700, 100)};
        Point[] allyBot = {new Point(700, 500), new Point(700, 100)};
        allEntities.add(new Minion(100, 500, allyTop, Team.ALLY, Lane.TOP));
        allEntities.add(new Minion(100, 500, allyMid, Team.ALLY, Lane.MID));
        allEntities.add(new Minion(100, 500, allyBot, Team.ALLY, Lane.BOT));

        Point[] enemyTop = {new Point(100, 100), new Point(100, 500)};
        Point[] enemyMid = {new Point(100, 500)};
        Point[] enemyBot = {new Point(700, 500), new Point(100, 500)};
        allEntities.add(new Minion(700, 100, enemyTop, Team.ENEMY, Lane.TOP));
        allEntities.add(new Minion(700, 100, enemyMid, Team.ENEMY, Lane.MID));
        allEntities.add(new Minion(700, 100, enemyBot, Team.ENEMY, Lane.BOT));
    }

    private void updateGameLogic() {
        if (gameOver) return; 
        if (frameCount % 600 == 0) spawnMinionWave();
        frameCount++;

        // 【新增】計算防禦塔的保護狀態 (如果防禦塔攻擊範圍內沒有敵方小兵，則開啟護盾)
        for (GameObject obj : allEntities) {
            if (obj instanceof Tower && !obj.isDead) {
                Tower t = (Tower) obj;
                t.isProtected = true; // 預設開啟保護
                
                for (GameObject m : allEntities) {
                    if (m instanceof Minion && m.team != t.team && m.active && !m.isDead) {
                        if (Math.hypot(m.x - t.x, m.y - t.y) <= t.attackRange) {
                            t.isProtected = false; // 範圍內有敵軍小兵，解除保護
                            break;
                        }
                    }
                }
            }
        }

        boolean allyNexusAlive = false;
        boolean enemyNexusAlive = false;
        List<GameObject> allies = new ArrayList<>();
        List<GameObject> enemies = new ArrayList<>();
        
        for (GameObject obj : allEntities) {
            if (!obj.active) continue;
            if (obj.team == Team.ALLY) allies.add(obj);
            else enemies.add(obj);
            if (obj instanceof Tower && ((Tower)obj).type == BuildingType.NEXUS) {
                if (obj.team == Team.ALLY) allyNexusAlive = true;
                if (obj.team == Team.ENEMY) enemyNexusAlive = true;
            }
        }

        if (!enemyNexusAlive) { gameOver = true; endMessage = "VICTORY"; }
        else if (!allyNexusAlive) { gameOver = true; endMessage = "DEFEAT"; }

        for (GameObject obj : allEntities) {
            if (!obj.active) continue; 
            List<GameObject> targetList = (obj.team == Team.ALLY) ? enemies : allies;
            obj.update(this, targetList); 
        }

        for (Projectile p : projectiles) {
            p.update();
            List<GameObject> targetList = (p.team == Team.ALLY) ? enemies : allies;
            for (GameObject target : targetList) {
                if (target.isDead) continue; 
                if (p.lane != Lane.NONE && target.lane != Lane.NONE && p.lane != target.lane) continue;
                
                if (p.active && target.active && target.hp > 0 && Math.hypot(p.x - target.x, p.y - target.y) < p.size / 2 + 15) {
                    // 【新增】如果玩家或 AI 的技能打中防禦塔，且防禦塔有護盾，則不造成傷害
                    if (target instanceof Tower && ((Tower) target).isProtected) {
                        p.active = false; // 招式依然會被擋下消耗掉
                        continue;
                    }
                    target.takeDamage(p.damage); p.active = false; 
                }
            }
        }

        for (GameObject obj : allEntities) {
            if (!obj.isDead && obj.hp <= 0) obj.die(); 
        }
        allEntities.removeIf(obj -> !obj.active);
        projectiles.removeIf(p -> !p.active);
    }

    private String getLaneName(Lane lane) {
        switch(lane) {
            case TOP: return "上路"; case MID: return "中路"; case BOT: return "下路"; default: return "未知";
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        for (GameObject obj : allEntities) obj.draw(g);
        for (Projectile p : projectiles) p.draw(g);

        g.setFont(new Font("Arial", Font.BOLD, 14));
        int allyUiY = 30;
        int enemyUiY = 30;

        for (GameObject obj : allEntities) {
            if (obj.isDead && (obj instanceof Player || obj instanceof BotPlayer)) {
                int respawnTimer = (obj instanceof BotPlayer) ? ((BotPlayer)obj).respawnTimer : ((Player)obj).respawnTimer;
                int secondsLeft = (int) Math.ceil((double) respawnTimer / 60.0);
                
                if (obj.team == Team.ALLY) {
                    g.setColor(Color.BLUE);
                    String name = (obj instanceof Player) ? "玩家" : ("AI (" + getLaneName(obj.lane) + ")");
                    g.drawString(name + " 復活: " + secondsLeft + "s", 20, allyUiY);
                    allyUiY += 20;
                } else {
                    g.setColor(Color.RED);
                    String name = "AI (" + getLaneName(obj.lane) + ")";
                    g.drawString(name + " 復活: " + secondsLeft + "s", 680, enemyUiY);
                    enemyUiY += 20;
                }
            }
        }
        
        if (!player.isDead) {
            g.setColor(Color.WHITE);
            g.fillRect(10, 480, 120, 100);
            g.setColor(Color.BLACK);
            g.drawRect(10, 480, 120, 100);
            
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString("Q 普攻: " + (player.qCooldown > 0 ? (int)Math.ceil(player.qCooldown/60.0) + "s" : "準備就緒"), 15, 500);
            g.drawString("W 治癒: " + (player.wCooldown > 0 ? (int)Math.ceil(player.wCooldown/60.0) + "s" : "準備就緒"), 15, 520);
            g.drawString("E 狙擊: " + (player.eCooldown > 0 ? (int)Math.ceil(player.eCooldown/60.0) + "s" : "準備就緒"), 15, 540);
            g.drawString("R 核爆: " + (player.rCooldown > 0 ? (int)Math.ceil(player.rCooldown/60.0) + "s" : "準備就緒"), 15, 560);
        }

        if (gameOver) {
            g.setColor(new Color(0, 0, 0, 150)); g.fillRect(0, 0, 800, 600);
            g.setFont(new Font("Arial", Font.BOLD, 80));
            g.setColor(endMessage.equals("VICTORY") ? Color.YELLOW : Color.RED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(endMessage, (800 - fm.stringWidth(endMessage)) / 2, 300);
        }
    }
}
