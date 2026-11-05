package dev.playerjoinnotifier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * 接続中サーバーのアドレスを得る唯一の場所。
 * マルチプレイ画面やダイレクト接続で「入力したとおりの文字列」（SRV 解決前）を返す。
 * Minecraft 側の API が変わったらここだけ直せばよい。
 */
final class CurrentServer {
	private CurrentServer() {
	}

	/** マルチプレイのサーバーに接続していなければ null。 */
	static String address(Minecraft mc) {
		ServerData data = mc.getCurrentServer();
		return data == null ? null : data.ip;
	}
}
