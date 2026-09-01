$path = "src/main/java/net/datasa/tanoshimi/util/RealGeminiClient.java"
$content = [System.IO.File]::ReadAllText($path)
[System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($False)))
