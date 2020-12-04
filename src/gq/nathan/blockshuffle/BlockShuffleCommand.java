package gq.nathan.blockshuffle;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;

public class BlockShuffleCommand implements CommandExecutor, TabCompleter {
	private final BlockShuffle blockShuffle;
	public BlockShuffleCommand(BlockShuffle blockShuffle) {
		this.blockShuffle = blockShuffle;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!sender.hasPermission("blockshuffle.manage")) {
			sender.sendMessage(ChatColor.RED + blockShuffle.msg("messages.permission_denied"));
			return true;
		}
		if(args.length > 0) {
			switch (args[0].toLowerCase()) {
				case "start": {
					sender.sendMessage(ChatColor.GREEN + blockShuffle.msg("messages.started"));
					blockShuffle.start();
				} return true;
				case "stop": {
					sender.sendMessage(ChatColor.GREEN + blockShuffle.msg("messages.stopped"));
					blockShuffle.stop();
				} return true;
			}
		}
		sender.sendMessage(ChatColor.RED + blockShuffle.msg("messages.usage", command.getUsage()));
		return true;
	}
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		return Arrays.asList("start", "stop");
	}
}
