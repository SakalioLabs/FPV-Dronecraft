# FPV Dronecraft 進展歸檔（繁體中文整理版）

本頁是原 README 進展訊息的繁體中文整理版。完整原始中文流水記錄保存在 [progress.zh-CN.md](progress.zh-CN.md)。

## 2026-06-23：v0.1.0 發布與文件整理

- 建立 `v0.1.0` tag，並發布 GitHub Release。
- Release 附件包含 `fpv-dronecraft-fabric-0.1.0.jar` 和 sources jar。
- README 從開發進展流改寫為專案介紹頁，並加入 Logo、四語導覽、安裝與快速開始資訊。

## 2026-06-23：偏航折返修復

- 修正持續偏航超過 90 度時，Minecraft Entity yaw 由 Euler Y 角折返造成的左右擺動。
- 新增四元數前向量水平投影的 heading 提取工具。
- Playable、simulation telemetry 與 adapter resolved-state 同步均使用新的 heading 投影。
- 新增回歸測試，覆蓋超過 90 度與 ACRO 偏航超過 120 度後不折返。

## 2026-06-22：V1 統一飛行模型門禁

- Playable 路徑加入逐 tick route-equivalence，對比 `DirectRouteHarness` 與 `LegacyPlayableFlightModelAdapter -> FlightModelRouter -> step`。
- Simulation 路徑加入逐 tick route-equivalence，對比直接 `DronePhysics` 與 `SimulationFlightModelAdapter -> FlightModelRouter -> step`。
- 新增實際執行的 `DroneEntity` GameTest，覆蓋初始化、連續 tick、reset、collision-free movement、resolved state 寫回、model ID/capabilities 與有限值檢查。
- 採用運行時模型切換策略 1：既有 `DroneEntity` 固定並持久化自己的 `flight_model_id`，全域 debug model 只影響之後新生成的實體。
- 新增 GitHub Actions CI 矩陣，覆蓋 core/fabric tests、full build、golden/route-equivalence、dependency boundary、serialization round-trip、GameTest 和 server self-tests。

## 2026-06-22：真實遙控器輸入鏈路

- 修正校準頁與實際飛行各自選擇 GLFW joystick 的問題。
- 加入 GUID/name 優先的 controller device resolver，GLFW ID 僅作為當前會話索引。
- 校準頁顯示並可切換遙控輸入啟用狀態；禁用時不再偽裝成可飛行。
- 加入 controller diagnostics，記錄 raw axes、mapping、calibration、shaping、smoothing、InputSource、arm-block reason、payload 與服務端接收狀態。
- 加入 fake provider 自動測試，覆蓋多設備、熱插拔、反向油門、低油門解鎖、高油門拒絕與 keyboard-only 回歸。

## 2026-06-21：ACRO 操作手感與三維姿態收斂

- 持續收斂「翻滾一圈後持續側飛」、「高速斜飛像平移」、「大 bank 下 pitch/roll/yaw 不像同一個三維剛體」等問題。
- 參考 `do-a-barrel-roll` 的姿態處理思路，把 ACRO 的體軸角速度投影到更連續的姿態/航向變化中。
- 加入多輪 sidewash、crossflow、dynamic inflow、weathercock yaw、rotor flapping、advance-ratio thrust loss、turn-load 與 gyro/load 相關回歸。
- 保留 ACRO 不自動回正、不偷主動 yaw、不粗暴加全域 drag 的產品語義。

## 研究資料與後續方向

- `docs/fpv-sim-model-validation.md` 持續整理 RotorPy、UIUC/RMIT/IMAV、RATM、Blackbird、APdrone、ZJU ground effect、NASA/電池/ESC 等資料來源。
- V2 留存方向包括：數值模型合併、canonical state/config 所有權收斂、推進器/電機介面統一、環境與碰撞輸入統一，以及更多真實硬體手感驗收。
