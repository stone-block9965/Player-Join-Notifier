import dev.playerjoinnotifier.dbus.DBusNotificationClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 実際の dbus-daemon（とモックの通知サービス）に対して通知を 1 回送る。run-dbus-integration-test.sh から使う。 */
public class DBusIntegrationTest {
	public static void main(String[] args) throws Exception {
		Path socket = args.length > 0 && !args[0].isEmpty()
				? Path.of(args[0])
				: DBusNotificationClient.findSessionBusSocket();
		// ロケールに依存しないよう、本文は UTF-8 ファイル経由で渡す
		String body = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
		DBusNotificationClient.notify(socket, "Minecraft", "applications-games",
				"Minecraft - プレイヤーが参加しました", body, 5000);
		System.out.println("notify OK");
	}
}
