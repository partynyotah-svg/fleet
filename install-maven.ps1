$ProgressPreference = 'SilentlyContinue'
$installDir = 'C:\tools'
$mavenZip = Join-Path $installDir 'apache-maven-3.9.9-bin.zip'
$mavenHome = Join-Path $installDir 'apache-maven-3.9.9'
$mvnCmd = Join-Path $mavenHome 'bin\mvn.cmd'

if (-not (Test-Path $mvnCmd)) {
    New-Item -ItemType Directory -Force -Path $installDir | Out-Null
    Invoke-WebRequest -Uri 'https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip' -OutFile $mavenZip
    Expand-Archive -Path $mavenZip -DestinationPath $installDir -Force
}

$env:Path = $mavenHome + '\bin;' + $env:Path
mvn -version
