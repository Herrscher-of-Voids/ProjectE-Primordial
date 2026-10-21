"""用户参考图的九种黑洞材料：本地抠图、换色、透明像素 PNG、模型和配方。
变更记录 #1：2026-10-14 02:02:18 · 1.2.0-blackhole-equipment-expansion
删除：无。新增：从用户 JPG 的主体轮廓提取同形九色材料，并合成到装备链。
修改：不处理装备/工具纹理；只保留参考图黑洞主体，棋盘格不作为透明图纹理。
用途：可重复生成资源。原图路径由参数传入；JSON/PNG 变更记入 changelog.json。
"""
from pathlib import Path
import argparse, colorsys, json
import os
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
MATERIALS=[
 ('stellar','恒星级黑洞','Stellar Black Hole',0.10,'minecraft:blaze_powder'),
 ('intermediate','中等质量黑洞','Intermediate-mass Black Hole',0.50,'minecraft:diamond'),
 ('supermassive','超大质量黑洞','Supermassive Black Hole',0.61,'minecraft:netherite_ingot'),
 ('ultramassive','极大质量黑洞','Ultramassive Black Hole',0.76,'minecraft:dragon_breath'),
 ('primordial','原初黑洞','Primordial Black Hole',0.34,'minecraft:echo_shard'),
 ('schwarzschild','史瓦西黑洞','Schwarzschild Black Hole',0.13,'minecraft:obsidian'),
 ('kerr','克尔黑洞','Kerr Black Hole',0.98,'minecraft:ender_eye'),
 ('reissner_nordstrom','赖斯纳–诺德斯特洛姆黑洞','Reissner–Nordström Black Hole',0.55,'minecraft:lightning_rod'),
 ('kerr_newman','克尔–纽曼黑洞','Kerr–Newman Black Hole',0.88,'minecraft:amethyst_shard')]

def preview_font(size):
 font_path=os.environ.get('PRIMORDIAL_PREVIEW_FONT')
 if not font_path and os.environ.get('WINDIR'):
  font_path=str(Path(os.environ['WINDIR'])/'Fonts'/'msyh.ttc')
 if not font_path or not Path(font_path).is_file():
  raise FileNotFoundError('Set PRIMORDIAL_PREVIEW_FONT to a CJK-capable font file.')
 return ImageFont.truetype(font_path,size)

def save_json(path,data):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def recipes():
 dest=ROOT/'src/main/resources/data/primordial/recipes'
 def recipe(name,pattern,keys):
  save_json(dest/(name+'.json'),{'type':'minecraft:crafting_shaped','category':'equipment','pattern':pattern,'key':{k:{'item':v} for k,v in keys.items()},'result':{'item':'primordial:'+name}})
 # 最新确认：原初黑洞为收集器首阶，其余八种包裹中心装备。
 recipe('blackhole_material_primordial',[' R ','IBI',' N '],{'R':'projecte:red_matter','I':'minecraft:echo_shard','B':'primordial:blackhole','N':'minecraft:nether_star'})
 variants=[m[0] for m in MATERIALS if m[0]!='primordial']
 for id in variants:
  old=dest/('blackhole_material_'+id+'.json')
  if old.exists():old.unlink()
 tiers=['primordial','stellar','intermediate','supermassive','ultramassive','schwarzschild','reissner_nordstrom','kerr','kerr_newman']
 conversion={'comment':'Change #3 2026-10-14 16:05:00: Sequential collector upgrades consume the preceding tier; total EMC doubles per tier. No direct primordial-to-variant graph.',
  'values':{'after':{'primordial:blackhole_material_'+id:3733899*(2**index) for index,id in enumerate(tiers)}},
  'groups':{'blackhole_collector_upgrade':{'conversions':[{'output':'primordial:blackhole_material_'+id,'ingredients':['primordial:blackhole_material_'+tiers[index]]} for index,id in enumerate(tiers[1:])]}}}
 save_json(ROOT/'src/main/resources/data/primordial/pe_custom_conversions/blackhole_materials.json',conversion)
 centers={name:'projecte:gem_'+name for name in ['helmet','chestplate','leggings','boots']}
 centers.update({name:'projecte:rm_'+name for name in ['pick','axe','shovel','hoe','shears','sword','hammer','katar','morning_star']})
 centers['bow']='minecraft:bow'
 for name,center in centers.items():
  keys={letter:'primordial:blackhole_material_'+id for letter,id in zip('ABCDEFGH',variants)};keys['P']=center
  recipe('blackhole_'+name,['ABC','DPE','FGH'],keys)

def textures(source):
 # 参考图的像素方格约 14px。按块取样后逐行找主体边缘，填实圆环内黑色区域。
 image=Image.open(source).convert('RGB').resize((56,84),Image.Resampling.BOX)
 sprite=Image.new('RGBA',(56,34))
 for y in range(25,57):
  row=[image.getpixel((x,y)) for x in range(56)]
  solid=[x for x,(r,g,b) in enumerate(row) if max(r,g,b)<165 or (r-b>28 and r>g)]
  if not solid:continue
  for x in range(min(solid),max(solid)+1):sprite.putpixel((x,y-24),(*row[x],255))
 assets=ROOT/'src/main/resources/assets/primordial'
 preview=Image.new('RGB',(960,660),'#151923');draw=ImageDraw.Draw(preview)
 font=preview_font(16)
 for index,(id,zh,en,hue,_) in enumerate(MATERIALS):
  result=Image.new('RGBA',(64,64))
  for y in range(sprite.height):
   for x in range(sprite.width):
    r,g,b,a=sprite.getpixel((x,y))
    if not a:continue
    _,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
    # 中心保持黑色，环与盘换色；高亮仍为相应色调的近白光。
    saturation=min(1,s*1.15)
    if id=='schwarzschild':saturation*=0.12
    if id=='reissner_nordstrom':saturation=min(1,s*1.75)
    rgb=colorsys.hsv_to_rgb(hue,saturation,v)
    result.putpixel((x+4,y+15),tuple(round(c*255) for c in rgb)+(255,))
  path=assets/'textures/item'/('blackhole_material_'+id+'.png');path.parent.mkdir(parents=True,exist_ok=True);result.save(path)
  save_json(assets/'models/item'/('blackhole_material_'+id+'.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'primordial:item/blackhole_material_'+id}})
  x=(index%3)*320;y=(index//3)*220
  preview.paste(result.resize((256,256),Image.Resampling.NEAREST),(x+32,y-24),result.resize((256,256),Image.Resampling.NEAREST))
  draw.text((x+160,y+192),zh,font=font,fill='#eeeeee',anchor='mm')
 out=ROOT/'design-discussion/blackhole-materials-preview.png';out.parent.mkdir(exist_ok=True);preview.save(out)
 print(out)

def charm():
 # 变更记录 #2：2026-10-14 02:08:58 · 用户追加重画护符。
 # 保留原物品 ID/功能，参考图的紫色黑洞嵌入银质八角底座并加吊环。
 assets=ROOT/'src/main/resources/assets/primordial/textures/item'
 result=Image.new('RGBA',(64,64));draw=ImageDraw.Draw(result)
 def octagon(x0,y0,x1,y1,c,fill):
  draw.polygon([(x0+c,y0),(x1-c,y0),(x1,y0+c),(x1,y1-c),(x1-c,y1),(x0+c,y1),(x0,y1-c),(x0,y0+c)],fill=fill)
 octagon(26,2,37,17,3,'#232631');octagon(27,3,36,16,2,'#aab4c4');octagon(29,5,34,13,1,'#222536')
 draw.line([(28,4),(34,4)],fill='#e9edf5',width=1)
 draw.rectangle((29,14,34,21),fill='#626980');draw.rectangle((30,14,31,20),fill='#d3d6ed')
 octagon(10,19,53,61,10,'#181923');octagon(11,20,52,60,9,'#6b7089')
 octagon(13,22,50,58,8,'#bdc2d2');octagon(15,24,48,56,7,'#323043')
 draw.line([(21,22),(42,22),(48,28)],fill='#ece9ff',width=1)
 draw.line([(15,49),(22,56),(42,56),(48,49)],fill='#4b3b65',width=2)
 # 同一参考图主体直接嵌入，避免另造不相关的黑洞形状。
 core=Image.open(assets/'blackhole_material_ultramassive.png').convert('RGBA')
 core=core.crop(core.getbbox()).resize((54,32),Image.Resampling.NEAREST)
 result.alpha_composite(core,(5,24))
 for x,y in [(15,28),(48,28),(19,54),(44,54)]:
  draw.rectangle((x,y,x+1,y+1),fill='#b594e7')
 result.save(assets/'blackhole_charm.png')
 preview=Image.new('RGB',(480,480),'#151923')
 enlarged=result.resize((384,384),Image.Resampling.NEAREST);preview.paste(enlarged,(48,24),enlarged)
 draw=ImageDraw.Draw(preview);font=preview_font(23)
 draw.text((240,447),'黑洞护符',font=font,fill='#eeeeee',anchor='mm')
 out=ROOT/'design-discussion/blackhole-charm-preview.png';preview.save(out);print(out)

def singularity_texture():
 # 变更记录 #4 2026-10-14 02:49:18：三维实体网格的球面与吸积盘像素材质。
 out=Image.new('RGBA',(64,32))
 for y in range(32):
  for x in range(64):
   if x<32:
    shade=2+((x*7+y*3)%4);rgb=(shade,shade,shade+2)
   else:
    t=(x-32)/31;glow=(0.5+0.5*__import__('math').cos((t-0.5)*__import__('math').pi*2))**0.4
    stripe=0.85+((y*13+x*7)%7)/40
    rgb=(int((65+190*glow)*stripe),int((35+207*glow)*stripe),int((80+142*glow)*stripe))
   out.putpixel((x,y),(*rgb,255))
 p=ROOT/'src/main/resources/assets/primordial/textures/entity/singularity.png';p.parent.mkdir(parents=True,exist_ok=True);out.save(p)

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--reference',type=Path);parser.add_argument('--recipes-only',action='store_true');args=parser.parse_args()
 recipes()
 if not args.recipes_only:
  if not args.reference:parser.error('--reference is required unless --recipes-only')
  textures(args.reference)
  charm()
  singularity_texture()

# 变更记录 #3 2026-10-14 02:26:13：按用户确认移除八材料工作台配方，添加ProjectE凝聚EMC映射，十四装备统一八种材料包围原工具/宝石护甲。
# 变更记录 #5 2026-10-14 16:05:00 · 1.2.0-entity-emc-fusion：删除生成器脚本的原初直达任意黑洞映射，新增九阶固定翻倍价格及前阶到后阶转换；保持装备纹理与八材料中心配方，避免重新生成时退回旧兑换图。
