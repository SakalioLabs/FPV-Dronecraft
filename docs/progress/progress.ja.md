# FPV Dronecraft 進捗アーカイブ（日本語要約）

このページは、以前ルート README にあった進捗ログを日本語で整理した要約です。完全な中国語原文は [progress.zh-CN.md](progress.zh-CN.md) に保存しています。

## 2026-06-23: v0.1.0 リリースとドキュメント整理

- `v0.1.0` tag を作成し、GitHub Release を公開しました。
- Release には `fpv-dronecraft-fabric-0.1.0.jar` と sources jar を添付しました。
- ルート README を進捗ログからプロジェクト紹介ページへ作り直し、Logo、言語ナビゲーション、インストール、クイックスタート、ドキュメントリンクを追加しました。

## 2026-06-23: ヨー角折り返しの修正

- 姿勢 quaternion を Euler Y 角に変換したとき、+/-90 度を超えると値が折り返され、左右に揺れる問題を修正しました。
- 機体の前方向ベクトルを水平面へ投影して heading を取り出す処理を追加しました。
- playable entity yaw、simulation telemetry yaw、adapter の resolved-state yaw を新しい heading 投影へ切り替えました。
- 90 度超えの heading と ACRO で 120 度を超えるヨー操作の回帰テストを追加しました。

## 2026-06-22: V1 統一フライトモデル門番

- Playable 経路で `DirectRouteHarness` と `LegacyPlayableFlightModelAdapter -> FlightModelRouter -> step` の tick 単位 equivalence test を追加しました。
- Simulation 経路で直接 `DronePhysics` と `SimulationFlightModelAdapter -> FlightModelRouter -> step` の tick 単位 equivalence test を追加しました。
- `DroneEntity` の実行 GameTest を追加し、初期化、連続 tick、reset、collision-free movement、resolved-state 書き戻し、model ID/capabilities、有限値検査を確認しました。
- ランタイム切り替え方針は option 1 を採用しました。既存の `DroneEntity` は自分の `flight_model_id` を固定・永続化し、全体 debug model の変更は新しく生成される entity にだけ影響します。
- GitHub Actions CI に core/fabric tests、full build、golden/route-equivalence、dependency boundary、serialization round-trip、GameTest、server self-tests を追加しました。

## 2026-06-22: 実機コントローラー入力経路

- 校正画面と実際の飛行経路が別々の GLFW joystick を選ぶ可能性を修正しました。
- GUID/name を優先する controller device resolver を追加し、GLFW ID は現在セッション内だけの index としました。
- 校正/設定画面にコントローラー入力の有効/無効状態を表示し、その場で切り替えられるようにしました。
- raw axes、mapping、calibration、shaping、smoothing、InputSource、arm-block reason、payload、サーバー受信入力を追える diagnostics を追加しました。
- fake provider テストで、多デバイス、再接続、スロットル反転、低スロットル arm、高スロットル拒否、ボタン edge、GAMEPAD arbitration、keyboard-only 回帰を確認しました。

## 2026-06-21: ACRO 操作感と 3D 姿勢の収束

- full roll 後の持続的な横滑り、高速斜め飛行が平面移動のように見える問題、大きな bank で pitch/roll/yaw が一つの 3D 剛体として感じられない問題を段階的に修正しました。
- `do-a-barrel-roll` の考え方を参考に、pitch/yaw/roll を画面平面のスライダーとして扱うのではなく、現在の body frame を中心に回転させる方針へ寄せました。
- sidewash、crossflow、dynamic inflow、weathercock yaw、rotor flapping、advance-ratio thrust loss、turn load、gyro/load などの回帰テストを追加しました。
- ACRO の意味は維持しています。自動水平化は追加せず、能動的な yaw 入力を奪わず、単純な全体 drag 増加にも頼っていません。

## 研究データと次の方向

- `docs/fpv-sim-model-validation.md` には RotorPy、UIUC/RMIT/IMAV、RATM、Blackbird、APdrone、ZJU ground effect、NASA、battery、ESC などの参照資料を整理しています。
- V2 では、数値モデルの収束、canonical state/config 所有権、推進器/モーター interface、環境/衝突入力、実機操作感の追加検証が残っています。
