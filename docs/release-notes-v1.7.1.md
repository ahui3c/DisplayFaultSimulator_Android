# Display Fault Simulator 1.7.1

## 繁體中文

### 線上自動更新

- 主畫面新增「檢查線上更新」功能
- 按下後立即檢查 `ahui3c/DisplayFaultSimulator_Android` 的最新 GitHub Release
- 發現較新版本時自動下載 APK，無需再次按下下載按鈕
- 下載完成後自動驗證 SHA-256、應用程式包名、版本碼及簽署憑證
- 驗證成功後開啟 Android 系統更新安裝畫面
- 支援下載中斷接續、未知來源安裝權限引導及更新後暫存檔清理
- 固定沿用既有發布簽章，修正「應用程式套件與現有套件衝突」而無法覆蓋安裝的問題

> Android 基於安全規範，最後安裝步驟仍必須由使用者在系統安裝畫面確認。

## English

### Online automatic updates

- Added **Check for updates** to the main screen
- Checks the latest GitHub Release from `ahui3c/DisplayFaultSimulator_Android` immediately after opening
- Automatically downloads a newer APK without requiring another download action
- Verifies the SHA-256 digest, application package, version code, and signing certificate
- Opens Android's system update installer after verification
- Supports interrupted-download recovery, unknown-source permission guidance, and post-update cleanup
- Reuses the existing distribution certificate to fix package-conflict errors during in-place updates

> Android security rules still require the user to confirm the final installation on the system installer screen.
