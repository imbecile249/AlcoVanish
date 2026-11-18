package local.alcovanish;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

/** Sends one configured RP message to the online player list, one command at a time. */
final class PrivateMessageBroadcast {
	private static final Queue<String> RECIPIENTS = new ArrayDeque<>();
	private static String message = "";
	private static int total;
	private static int sent;
	private static long nextSendAt;
	private static boolean running;

	private PrivateMessageBroadcast() { }

	static void start(Minecraft client) {
		stop(false);
		if (client.player == null || client.getConnection() == null) {
			status(client, "Подключись к серверу перед запуском рассылки");
			return;
		}
		message = NearbyWorkshopClient.CONFIG.pmMessage.trim();
		if (message.isBlank()) {
			status(client, "Сначала укажи текст личного сообщения");
			return;
		}
		String ownName = client.player.getGameProfile().name();
		Set<String> excluded = new HashSet<>();
		for (String name : NearbyWorkshopClient.CONFIG.pmExcludedPlayers.split("[,;\\s]+")) {
			if (!name.isBlank()) excluded.add(name.toLowerCase(Locale.ROOT));
		}
		LinkedHashSet<String> names = new LinkedHashSet<>();
		client.getConnection().getOnlinePlayers().stream()
				.map(info -> info.getProfile().name())
				.filter(name -> !name.equalsIgnoreCase(ownName))
				.filter(name -> !excluded.contains(name.toLowerCase(Locale.ROOT)))
				.sorted(Comparator.naturalOrder())
				.forEach(names::add);
		RECIPIENTS.addAll(names);
		total = RECIPIENTS.size();
		sent = 0;
		if (total == 0) {
			status(client, "В списке сервера нет других игроков");
			return;
		}
		running = true;
		nextSendAt = 0;
		status(client, "Рассылка запущена: " + total + " игроков");
	}

	static void stop(Minecraft client) {
		stop(true);
		status(client, "Рассылка остановлена");
	}

	private static void stop(boolean clearStatus) {
		RECIPIENTS.clear();
		running = false;
		nextSendAt = 0;
		if (clearStatus) sent = 0;
	}

	static void tick(Minecraft client) {
		if (!running) return;
		if (client.player == null || client.getConnection() == null) {
			stop(client);
			return;
		}
		if (System.currentTimeMillis() < nextSendAt) return;
		String recipient = RECIPIENTS.poll();
		if (recipient == null) {
			running = false;
			status(client, "Рассылка завершена: " + sent + " из " + total);
			return;
		}
		client.getConnection().sendCommand("m " + recipient + " " + message);
		sent++;
		nextSendAt = System.currentTimeMillis() + NearbyWorkshopClient.CONFIG.pmDelayMs;
		if (RECIPIENTS.isEmpty()) {
			running = false;
			status(client, "Рассылка завершена: " + sent + " из " + total);
		}
	}

	static boolean isRunning() { return running; }
	static int sentCount() { return sent; }
	static int totalCount() { return total; }

	private static void status(Minecraft client, String text) {
		if (client.player != null) client.player.sendOverlayMessage(Component.literal(text));
	}
}
