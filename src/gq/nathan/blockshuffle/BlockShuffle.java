package gq.nathan.blockshuffle;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;
import java.util.stream.Collectors;

public class BlockShuffle extends JavaPlugin implements Listener {
	private final Multimap<Material, Material> alternatives = ArrayListMultimap.create();
	{
		alternatives.put(Material.ACACIA_SIGN, Material.ACACIA_WALL_SIGN);
		alternatives.put(Material.BIRCH_SIGN, Material.BIRCH_WALL_SIGN);
		alternatives.put(Material.CRIMSON_SIGN, Material.CRIMSON_WALL_SIGN);
		alternatives.put(Material.DARK_OAK_SIGN, Material.DARK_OAK_WALL_SIGN);
		alternatives.put(Material.JUNGLE_SIGN, Material.JUNGLE_WALL_SIGN);
		alternatives.put(Material.OAK_SIGN, Material.OAK_WALL_SIGN);
		alternatives.put(Material.SPRUCE_SIGN, Material.SPRUCE_WALL_SIGN);
		alternatives.put(Material.WARPED_SIGN, Material.WARPED_WALL_SIGN);
		alternatives.put(Material.TORCH, Material.WALL_TORCH);
		alternatives.put(Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH);
		alternatives.put(Material.SOUL_TORCH, Material.SOUL_WALL_TORCH);
		alternatives.put(Material.WHITE_BANNER, Material.WHITE_WALL_BANNER);
		alternatives.put(Material.ORANGE_BANNER, Material.ORANGE_WALL_BANNER);
		alternatives.put(Material.MAGENTA_BANNER, Material.MAGENTA_WALL_BANNER);
		alternatives.put(Material.LIGHT_BLUE_BANNER, Material.LIGHT_BLUE_WALL_BANNER);
		alternatives.put(Material.YELLOW_BANNER, Material.YELLOW_WALL_BANNER);
		alternatives.put(Material.LIME_BANNER, Material.LIME_WALL_BANNER);
		alternatives.put(Material.PINK_BANNER, Material.PINK_WALL_BANNER);
		alternatives.put(Material.GRAY_BANNER, Material.GRAY_WALL_BANNER);
		alternatives.put(Material.LIGHT_GRAY_BANNER, Material.LIGHT_GRAY_WALL_BANNER);
		alternatives.put(Material.CYAN_BANNER, Material.CYAN_WALL_BANNER);
		alternatives.put(Material.PURPLE_BANNER, Material.PURPLE_WALL_BANNER);
		alternatives.put(Material.BLUE_BANNER, Material.BLUE_WALL_BANNER);
		alternatives.put(Material.BROWN_BANNER, Material.BROWN_WALL_BANNER);
		alternatives.put(Material.GREEN_BANNER, Material.GREEN_WALL_BANNER);
		alternatives.put(Material.RED_BANNER, Material.RED_WALL_BANNER);
		alternatives.put(Material.BLACK_BANNER, Material.BLACK_WALL_BANNER);
		alternatives.put(Material.WITHER_SKELETON_SKULL, Material.WITHER_SKELETON_WALL_SKULL);
		alternatives.put(Material.ANVIL, Material.CHIPPED_ANVIL);
		alternatives.put(Material.ANVIL, Material.DAMAGED_ANVIL);
	}
	
	public String msg(String key, Object ...args) {
		return String.format(Objects.requireNonNull(getConfig().getString(key)), args);
	}
	
	private final Map<Player, Material> playerTargets = new HashMap<>();
	private final Set<Player> completed = new HashSet<>();
	
	private boolean gameActive;
	private int roundNumber;
	private int startTime;
	
	private int endTask;
	
	private int roundLength() {
		if(roundNumber == 1) {
			return getConfig().getInt("first_round_time");
		} else {
			return getConfig().getInt("round_time");
		}
	}
	
	public int getTimeElapsed() {
		if(!gameActive) {
			return 0;
		}
		int currentTime = getServer().getCurrentTick();
		int timeElapsed = currentTime - startTime;
		return Math.min(timeElapsed, roundLength());
	}
	public int getTimeLeft() {
		return roundLength() - getTimeElapsed();
	}
	public double getProgress() {
		return getTimeLeft() / (double)roundLength();
	}
	
	private final Map<Player, BossBar> bossBars = new HashMap<>();
	public BossBar getBossBar(Player player) {
		BossBar bossBar = bossBars.get(player);
		if(bossBar != null) {
			return bossBar;
		}
		bossBar = getServer().createBossBar("", BarColor.PINK, BarStyle.SEGMENTED_10);
		bossBar.addPlayer(player);
		bossBars.put(player, bossBar);
		return bossBar;
	}
	public void removeBossBar(Player player) {
		BossBar bossBar = bossBars.get(player);
		if(bossBar != null) {
			bossBar.removeAll();
			bossBars.remove(player);
		}
	}
	
	private Objective objective;
	
	@Override
	public void onEnable() {
		saveDefaultConfig();
		
		{
			PluginCommand command = getCommand("blockshuffle");
			assert command != null;
			BlockShuffleCommand executor = new BlockShuffleCommand(this);
			command.setExecutor(executor);
			command.setTabCompleter(executor);
		}
		
		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			for(Player p : getPlayers()) {
				if(isPlaying(p)) {
					BossBar bossBar = getBossBar(p);
					String titleColor = "";
					if(isWorking(p)) {
						bossBar.setColor(BarColor.RED);
						titleColor += ChatColor.RED.toString() + ChatColor.BOLD.toString();
					} else {
						bossBar.setColor(BarColor.GREEN);
						titleColor += ChatColor.GREEN.toString() + ChatColor.BOLD.toString();
					}
					bossBar.setTitle(titleColor + msg("messages.title", roundNumber, getNiceName(playerTargets.get(p))));
					bossBar.setProgress(getProgress());
				}
			}
		}, 0, 1);
		
		getServer().getPluginManager().registerEvents(this, this);
	}
	@Override
	public void onDisable() {
		stop();
	}
	
	public Set<Player> getPlayers() {
		return getServer().getOnlinePlayers().stream().filter(this::canPlay).collect(Collectors.toSet());
	}
	public boolean canPlay(Player player) {
		return player.hasPermission("blockshuffle.play");
	}
	public boolean isPlaying(Player player) {
		return canPlay(player) && gameActive && playerTargets.containsKey(player);
	}
	public boolean isWorking(Player player) {
		return isPlaying(player) && !completed.contains(player);
	}
	
	public void start() {
		if(gameActive) {
			return;
		}
		gameActive = true;
		roundNumber = 0;
		
		Scoreboard scoreboard = getServer().getScoreboardManager().getMainScoreboard();
		String objectiveName = "blockshuffle";
		objective = scoreboard.getObjective(objectiveName);
		if(objective == null) {
			objective = scoreboard.registerNewObjective(objectiveName, "dummy", "Score");
		}
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		
		getServer().broadcastMessage(ChatColor.GREEN + msg("messages.starting", getPlayers().size()));
		nextRound();
	}
	public void cleanUpRound() {
		playerTargets.clear();
		completed.clear();
	}
	public void nextRound() {
		++roundNumber;
		
		startTime = getServer().getCurrentTick();
		
		for(Player p : getPlayers()) {
			Score score = objective.getScore(p.getDisplayName());
			if(!score.isScoreSet()) {
				score.setScore(0);
			}
			if(isWorking(p)) {
				getServer().broadcastMessage(ChatColor.RED + msg("messages.failed", p.getDisplayName()));
			}
		}
		
		cleanUpRound();
		
		getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
			if(!gameActive) {
				return;
			}
			
			for(Player p : getPlayers()) {
				assignBlock(p, Math.min(roundNumber / getConfig().getInt("difficulty_interval"), 2));
			}
			
			endTask = getServer().getScheduler().scheduleSyncDelayedTask(this, this::nextRound, roundLength());
		}, 20);
	}
	public void stop() {
		if(!gameActive) {
			return;
		}
		cleanUpRound();
		gameActive = false;
		
		for(Player p : getServer().getOnlinePlayers()) {
			removeBossBar(p);
			Objects.requireNonNull(objective.getScoreboard()).resetScores(p.getDisplayName());
		}
		
		objective.unregister();
		objective = null;
	}
	
	public static String getNiceName(Material m) {
		return Arrays.stream(m.toString().split("_"))
				.map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
				.collect(Collectors.joining(" "));
	}
	
	public void assignBlock(Player p, int difficulty) {
		Material m = randomBlock(difficulty);
		playerTargets.put(p, m);
		
		p.sendMessage(ChatColor.GREEN + msg("messages.block_chosen", getNiceName(m)));
		getLogger().info(String.format("%s must stand on %s.", p.getName(), m.toString()));
	}
	
	public Material randomBlock(int difficulty) {
		List<String> options = new ArrayList<>();
		switch (difficulty) {
			case 2: {
				options.addAll(getConfig().getStringList("end_game_blocks"));
			}
			case 1: {
				options.addAll(getConfig().getStringList("mid_game_blocks"));
			}
			case 0: {
				options.addAll(getConfig().getStringList("early_game_blocks"));
			}
		}
		assert options.size() > 0;
		String item = options.get((int)(Math.random() * options.size()));
		return Material.valueOf(item);
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		if(!isWorking(event.getPlayer())) {
			return;
		}
		
		Material targetBlock = playerTargets.get(event.getPlayer());
		
		Block playerBlock = event.getPlayer().getLocation().getBlock();
		for(int dy = -1 + (event.getPlayer().isSwimming() ? -2 : 0); dy <= 0; ++dy) {
			Material type = playerBlock.getRelative(0, dy, 0).getType();
			if(targetBlock == type || alternatives.get(targetBlock).contains(type)) {
				completeJob(event.getPlayer(), targetBlock);
			}
		}
	}
	
	private final Set<Firework> visualFireworks = new HashSet<>();
	
	@EventHandler
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		if(event.getDamager() instanceof Firework) {
			Firework firework = (Firework)event.getDamager();
			if(visualFireworks.contains(firework)) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
		if(event.getEntity() instanceof Firework) {
			Firework firework = (Firework)event.getEntity();
			visualFireworks.remove(firework);
		}
	}
	
	public void completeJob(Player player, Material targetBlock) {
		completed.add(player);
		getServer().broadcastMessage(ChatColor.LIGHT_PURPLE + msg("messages.block_found", player.getDisplayName(), getNiceName(targetBlock)));
		
		Firework firework = (Firework)player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK);
		FireworkMeta meta = firework.getFireworkMeta();
		
		meta.setPower(2);
		meta.addEffect(FireworkEffect.builder().withColor(Color.GREEN).flicker(true).build());
		
		firework.setFireworkMeta(meta);
		
		visualFireworks.add(firework);
		
		Score score = objective.getScore(player.getDisplayName());
		score.setScore(score.getScore() + 1);
		
		if(completed.size() == playerTargets.size()) {
			getServer().getScheduler().cancelTask(endTask);
			nextRound();
		}
	}
}
