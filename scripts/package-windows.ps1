$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$buildRoot = Join-Path $projectRoot "build"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$workDir = Join-Path $buildRoot ("package-work-" + $stamp)
$inputDir = Join-Path $workDir "input"
$classesDir = Join-Path $workDir "classes"
$runtimeDir = Join-Path $workDir "runtime"
$releaseDir = Join-Path $buildRoot ("release-" + $stamp)
$appName = "ForcaCooperativa"
$appDir = Join-Path $releaseDir $appName
$zipPath = Join-Path $releaseDir ($appName + "-win-x64.zip")
$distRoot = Join-Path $projectRoot "dist"
$distApp = Join-Path $distRoot $appName
$pendingApp = Join-Path $distRoot ($appName + ".pending")
$jarName = "hangman-rmi-game.jar"
$jarPath = Join-Path $inputDir $jarName
$mainClass = "br.edu.ucsal.forca.client.ForcaLauncherApp"

foreach ($toolName in @("javac.exe", "jar.exe", "jlink.exe", "jpackage.exe")) {
    if (-not (Get-Command $toolName -ErrorAction SilentlyContinue)) {
        throw "Nao encontrei $toolName. Instale e configure um JDK 17 ou mais novo."
    }
}

if ((Test-Path -LiteralPath $workDir) -or (Test-Path -LiteralPath $releaseDir)) {
    throw "A pasta de saida desta compilacao ja existe. Aguarde um segundo e execute novamente."
}
if (Test-Path -LiteralPath $pendingApp) {
    throw "A pasta temporaria ja existe: $pendingApp. Confira-a antes de continuar."
}

New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
New-Item -ItemType Directory -Path $distRoot -Force | Out-Null

$mainSources = @(Get-ChildItem (Join-Path $projectRoot "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })
if ($mainSources.Count -eq 0) {
    throw "Nao encontrei arquivos Java em src\main\java."
}

& javac --release 17 -encoding UTF-8 -d $classesDir @mainSources
if ($LASTEXITCODE -ne 0) {
    throw "A compilacao do jogo falhou."
}

& jar --create --file $jarPath --main-class $mainClass -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw "A criacao do JAR falhou."
}

& jlink --add-modules "java.base,java.desktop,java.rmi" --output $runtimeDir --strip-debug --no-header-files --no-man-pages --strip-native-commands --compress=2
if ($LASTEXITCODE -ne 0) {
    throw "A criacao do runtime Java reduzido falhou."
}

& jpackage --type app-image --name $appName --app-version 1.0.0 --description "Jogo cooperativo da Forca com Java RMI" --dest $releaseDir --input $inputDir --main-jar $jarName --main-class $mainClass --runtime-image $runtimeDir
if ($LASTEXITCODE -ne 0) {
    throw "O empacotamento do aplicativo falhou."
}

Compress-Archive -LiteralPath $appDir -DestinationPath $zipPath -CompressionLevel Optimal
New-Item -ItemType Directory -Path $pendingApp -Force | Out-Null
Copy-Item -Path (Join-Path $appDir "*") -Destination $pendingApp -Recurse -Force
if ((-not (Test-Path -LiteralPath (Join-Path $pendingApp ($appName + ".exe")))) -or (-not (Test-Path -LiteralPath (Join-Path $pendingApp "runtime\release"))) -or (-not (Test-Path -LiteralPath (Join-Path $pendingApp "app\hangman-rmi-game.jar")))) {
    throw "O pacote preparado para o repositorio esta incompleto."
}

$repoPrefix = [System.IO.Path]::GetFullPath($projectRoot).TrimEnd('\') + '\'
$distAppFull = [System.IO.Path]::GetFullPath($distApp)
if ((-not $distAppFull.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) -or ((Split-Path -Leaf $distAppFull) -ne $appName)) {
    throw "Destino do aplicativo fora da pasta prevista."
}
if (Test-Path -LiteralPath $distApp) {
    Remove-Item -LiteralPath $distApp -Recurse -Force
}
Move-Item -LiteralPath $pendingApp -Destination $distApp

Write-Output ("REPOSITORY_APP=" + (Join-Path $distApp ($appName + ".exe")))
Write-Output ("PORTABLE_ZIP=" + $zipPath)
