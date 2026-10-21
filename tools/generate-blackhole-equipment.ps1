# 【文件职责】从本实例 ProjectE 同版物品轮廓生成黑洞装备配方、模型和重配色像素资源。
# 【变更记录 #1】2026-10-14 01:00:00（第 1 次备注 · 1.2.0-blackhole-equipment-system）
# 删除：无；新增可复现的九工具、额外弓、四护甲、七模块的 JSON 和 PNG 生成。
# 修改：原生红物质轮廓重新配成黑曜石/青绿/紫色调，装备与护甲层保留原像素尺寸。
# 用途：测试实例与正式构建使用同一批资源；脚本不修改 ProjectE JAR 或其它模组文件。
# 【变更记录 #2】2026-10-14 01:18:34（1.2.0-equipment-emc-features）
# 删除：原创弓轮廓、实体模块资源与配方生成、按像素坐标添加花纹。
# 新增：从原版 1.20.1 弓纹理及模型直接改色，保留透明度与显示变换。
# 修改：所有工具/护甲来源于 ProjectE 对应红物质原图，仅变颜色；功能改为界面购买。
# 用途：严格遵循用户指定素材来源，不自行重画。
param(
    [Parameter(Mandatory=$true)][string]$ProjectEJar,
    [Parameter(Mandatory=$true)][string]$MinecraftJar
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project = Split-Path $PSScriptRoot -Parent
$assets = Join-Path $project 'src/main/resources/assets/primordial'
$recipes = Join-Path $project 'src/main/resources/data/primordial/recipes'
$utf8 = [Text.UTF8Encoding]::new($false)
function Save-Json([string]$path, [object]$data) {
    $directory = Split-Path $path -Parent
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    [IO.File]::WriteAllText($path, ($data | ConvertTo-Json -Depth 12) + "`n", $utf8)
}
$armor = [ordered]@{ helmet='rm_helmet'; chestplate='rm_chestplate'; leggings='rm_leggings'; boots='rm_boots' }
$tools = [ordered]@{ pick='rm_pick'; axe='rm_axe'; shovel='rm_shovel'; hoe='rm_hoe'; shears='rm_shears'; sword='rm_sword'; hammer='rm_hammer'; katar='rm_katar'; morning_star='rm_morning_star' }
# 新材料配方独立维护；重跑本脚本不得恢复旧的黑洞方块配方。
python (Join-Path $PSScriptRoot 'generate-blackhole-materials.py') --recipes-only
if ($LASTEXITCODE -ne 0) { throw 'Material recipe generation failed' }
foreach ($name in $armor.Keys) {
    Save-Json (Join-Path $assets "models/item/blackhole_$name.json") ([ordered]@{
        parent='minecraft:item/generated'; textures=@{layer0="primordial:item/blackhole_$name"}
    })
}
foreach ($name in $tools.Keys) {
    Save-Json (Join-Path $assets "models/item/blackhole_$name.json") ([ordered]@{
        parent='minecraft:item/handheld'; textures=@{layer0="primordial:item/blackhole_$name"}
    })
}
$zip = [IO.Compression.ZipFile]::OpenRead($ProjectEJar)
function Recolor([string]$source, [string]$destination) {
    $entry = $zip.GetEntry($source)
    if ($null -eq $entry) { throw "Missing ProjectE texture: $source" }
    $stream = $entry.Open()
    try {
        $original = [Drawing.Bitmap]::new($stream)
        try {
            $out = [Drawing.Bitmap]::new($original.Width, $original.Height, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
            try {
                for ($y=0; $y -lt $original.Height; $y++) {
                    for ($x=0; $x -lt $original.Width; $x++) {
                        $pixel = $original.GetPixel($x,$y)
                        if ($pixel.A -eq 0) { continue }
                        $light = [int](0.3*$pixel.R + 0.59*$pixel.G + 0.11*$pixel.B)
                        $accent = $pixel.R -gt ($pixel.G + 16) -and $pixel.R -gt ($pixel.B + 12)
                        if ($accent) {
                            $red = [Math]::Min(255, 28 + [int]($light*0.28))
                            $green = [Math]::Min(255, 80 + [int]($light*0.70))
                            $blue = [Math]::Min(255, 100 + [int]($light*0.68))
                        } else {
                            $red = [Math]::Min(255, 12 + [int]($light*0.48))
                            $green = [Math]::Min(255, 18 + [int]($light*0.53))
                            $blue = [Math]::Min(255, 29 + [int]($light*0.58))
                        }
                        $out.SetPixel($x,$y,[Drawing.Color]::FromArgb($pixel.A,$red,$green,$blue))
                    }
                }
                [IO.Directory]::CreateDirectory((Split-Path $destination -Parent)) | Out-Null
                $out.Save($destination,[Drawing.Imaging.ImageFormat]::Png)
            } finally { $out.Dispose() }
        } finally { $original.Dispose() }
    } finally { $stream.Dispose() }
}
try {
    $armorSource = [ordered]@{ helmet='head'; chestplate='chest'; leggings='legs'; boots='feet' }
    foreach ($name in $armorSource.Keys) {
        Recolor "assets/projecte/textures/item/rm_armor/$($armorSource[$name]).png" (Join-Path $assets "textures/item/blackhole_$name.png")
    }
    $toolSource = [ordered]@{ pick='pickaxe'; axe='axe'; shovel='shovel'; hoe='hoe'; shears='shears'; sword='sword'; hammer='hammer'; katar='katar'; morning_star='morning_star' }
    foreach ($name in $toolSource.Keys) {
        Recolor "assets/projecte/textures/item/rm_tools/$($toolSource[$name]).png" (Join-Path $assets "textures/item/blackhole_$name.png")
    }
    foreach ($layer in 1..2) {
        Recolor "assets/projecte/textures/models/armor/red_matter_layer_$layer.png" (Join-Path $assets "textures/models/armor/blackhole_layer_$layer.png")
    }
} finally { $zip.Dispose() }
$zip = [IO.Compression.ZipFile]::OpenRead($MinecraftJar)
try {
    foreach ($suffix in @('', '_pulling_0', '_pulling_1', '_pulling_2')) {
        Recolor "assets/minecraft/textures/item/bow$suffix.png" (Join-Path $assets "textures/item/blackhole_bow$suffix.png")
        $entry = $zip.GetEntry("assets/minecraft/models/item/bow$suffix.json")
        if ($null -eq $entry) { throw "Missing vanilla bow model: $suffix" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $model = $reader.ReadToEnd().Replace('minecraft:item/bow', 'primordial:item/blackhole_bow').Replace('"item/bow', '"primordial:item/blackhole_bow') | ConvertFrom-Json }
        finally { $reader.Dispose() }
        Save-Json (Join-Path $assets "models/item/blackhole_bow$suffix.json") $model
    }
} finally { $zip.Dispose() }
Write-Output 'Generated 23 material/equipment recipes, 17 item models and 19 recolored textures from ProjectE and vanilla.'

# 变更记录 #3 2026-10-14 02:04:37：移除旧配方生成并调用九材料脚本，保留装备纹理来源。
