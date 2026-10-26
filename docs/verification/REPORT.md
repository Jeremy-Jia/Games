# 2.0.0 验收记录

日期：2026-10-26（Asia/Shanghai）。设备：用户的小米 15，设备代号 `dada`，Android 17。

## 构建与升级

- JDK 17、Android SDK 36、AGP 8.10.1、Gradle 8.11.1，离线完成 Release、Debug 和 AndroidTest 构建。
- Release `lintVitalRelease` 通过。
- 原安装版本为碰界 1.3.0 / code 6，本版为 2.0.0 / code 7，包名仍为 `dev.brickzen.app`。
- 验证签名匹配后执行覆盖安装，未卸载原碰界。原 `zen_level=21`、`best_zen=189950`、外观/音效/震动/挡板高度设置保留。
- 大厅初版曾因过早访问未创建的窗口发生启动崩溃；已将系统栏设置移到 `setContentView` 后，最终版本冷启动与实际卡片点击正常。

## 引擎回归

`test.ps1` 运行两套纯 Java 测试，均通过：

- 碰界：360 关神秘砖布局、首次命中与反弹、冻结后不继续掉球、连锁冻结、完整局面及随机状态快照、奖励幂等与取消、最后一砖清关、非法快照拒绝。
- 星隙：移动边界、升级暂停与三选一有效性、实体数量上限、胜负终局、固定种子可重放、满额实体时 Boss 生成、限时奇遇完成/暂停/死亡/重置。

## 真机完整往返

使用仓库中的 `CollectionSmokeInstrumentation`，最终输出：

```text
Star challenge running: 0/30 s, level=1, hp=100
Star challenge running: 5/30 s, level=1, hp=100
Star challenge running: 10/30 s, level=1, hp=100
Star challenge running: 15/30 s, level=1, hp=100
Star challenge running: 20/30 s, level=1, hp=100
Star challenge running: 25/30 s, level=2, hp=100
Success and duplicate-result checks passed; verifying cancellation
PASS: collection portal smoke test; preferences_restored=true
```

测试从正常导出的大厅启动并点击真实碰界卡片，再在引擎中确定性命中神秘砖。后续切换由生产 `onDraw → openStarPortal` 路径自动触发；星隙使用真实绘制时钟运行 30 秒，仅自动选择升级，没有跳时间或改生命。结算时触摸真实「领取奖励」按钮，验证以下内容：

- 传送身份和已保存快照一致，原局在奇遇期间暂停。
- 30 秒后正常胜利，没有生成标准航程 Boss。
- 返回奖励等于结算界面显示的分数；成功后得到 14 秒宽板和满能量。
- 原关卡、生命、时间、球的位置与速度、实体数量和每块砖的血量完全保持。
- 重复投递相同结果不会重复发奖；返回保持暂停，等待玩家继续。
- 第二次奇遇主动取消，分数、生命及局面保留，不发成功道具。
- 测试关闭所有 Activity 后，在 `finally` 中恢复三份偏好，逐键比对成功；最后重新安装 Release。测试辅助包已移除。

HyperOS 会拦截测试进程第一次从后台直接打开内部 Activity，因此测试先通过系统 Shell 打开正常 Launcher 入口，再点击大厅卡片。设备锁屏或妙享挂起时焦点检查会失败，须本机解锁后运行。

截图是对真机已布局的应用 View 直接绘制的记录，不包含其他应用的悬浮窗和系统通知：

- [大厅](collection.png)
- [传送前局面](brick-before.png)
- [星隙奇遇](star-challenge.png)
- [奇遇结算](star-complete.png)
- [带奖励返回](brick-return.png)

## 本轮覆盖边界

引擎快照已在 JDK 和 Android 实际往返中恢复并比对。未在真实 30 秒挑战中主动杀死整个应用进程；进程重建后的补领入口经过源码检查，不能把这项等同于完整故障注入测试。星隙普通三分钟模式完成了引擎模拟与初版真机运行，集锦版的设备往返验收以 30 秒奇遇为主。

## 发布文件

`releases/Games-2.0.0-release.apk`

SHA-256：`060a54b111d747e6996af2524868f628456d1d0d526376d147ac91a1978fb6b2`

签名私钥、原 APK 和个人存档备份均未进入仓库。
