import sys; sys.path.insert(0,sys.argv[1])
from restroke import restroke, restroke_smooth, down, K
from PIL import Image
import numpy as np, cairosvg, io
S=sys.argv[1]
p='feature/main/src/main/res/drawable-nodpi/schema_tadpole_three_wheeler_top_view.webp'
src=np.array(Image.open('/tmp/claude-0/-home-user-TPMS-advanced-NE/43403585-3f5f-5e9e-ad58-22b6693c915c/images/7.webp').convert('RGBA').getchannel('A')).astype(float)
h,w=src.shape
# Symmetric about x=230.5: mirror the left half onto the right 
xs=np.arange(w); right=xs>230.5
orig=src.copy()
src[:,right]=src[:,461-xs[right]]
# ...but keep the original exhaust (silencer + heat shield) on the right, outside the body outline
from PIL import ImageDraw
mk=Image.new('L',(w,h),0); ImageDraw.Draw(mk).polygon([(392,755),(470,755),(470,995),(300,995),(318,965)],fill=255)
src=np.where(np.array(mk)>0,orig,src)
W,H=625,1386; s=1370/1047; cx=230.5; top=8
# output -> source affine at 4x
big=Image.fromarray(src.astype(np.uint8)).transform((W*K,H*K),Image.AFFINE,(1/(s*K),0,cx-312.5/s,0,1/(s*K),top-8/s),resample=Image.BICUBIC)
b=np.array(big)/255
# Source is cut at its last row: drop the resampled edge so the redrawn tail takes over
# only the outer tail curve reaches the cut: clear it below y=1008 in the tail area
Y0=int((8+(1008-top)*s)*K); X0=int((312.5+(186-cx)*s)*K); X1=int((312.5+(275-cx)*s)*K)
b[Y0:,X0:X1]=0
# the outer tail curve's last few rows curl at the cut: clear them from where the redraw starts
for xa,xb in ((180,198),(263,281)):
    b[int((8+(997-top)*s)*K):,int((312.5+(xa-cx)*s)*K):int((312.5+(xb-cx)*s)*K)]=0
a4=restroke_smooth(b,float(sys.argv[2]) if len(sys.argv)>2 else 1.6)
def T(x,y): return (312.5+(x-cx)*s)*K,(8+(y-top)*s)*K
def P(pts): return ' '.join(f'{X:.1f},{Y:.1f}' for X,Y in (T(x,y) for x,y in pts))
def sym(d):
    return f'<path d="{d}"/><path d="{d}" transform="translate({625*K},0) scale(-1,1)"/>'
# Tail tip completion + rear fender, in source coordinates, mirrored
tail=f"M {P([(188.5,996)])} C {P([(196,1013),(213,1026),(230.5,1027)])}"
fender=f"M {P([(209,1021)])} C {P([(209,1034),(213,1044),(220,1049)])} C {P([(224,1052),(227,1053),(230.5,1053)])}"
# The exhaust's upper mounting hole is too faint in the source to survive the re-stroke
hx,hy=T(404.3,826)
hole=f'<ellipse cx="{hx:.1f}" cy="{hy:.1f}" rx="{1.9*s*K:.1f}" ry="{4.6*s*K:.1f}" transform="rotate(-8 {hx:.1f} {hy:.1f})" stroke-width="{1.3*K}"/>'
svg=f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W*K}" height="{H*K}"><g fill="none" stroke="black" stroke-width="{1.7*K}" stroke-linecap="round">{sym(tail)}{hole}</g></svg>'''
f=np.array(Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode()))).getchannel('A')).astype(float)/255
a=down(np.maximum(a4,f)).astype(np.uint8)
Image.merge('RGBA',[Image.new('L',(W,H),0)]*3+[Image.fromarray(a)]).save(p,lossless=True,quality=100,method=6)
al=a/255
Image.fromarray((255-al*255).astype('uint8')).save(S+'/t7_light.png')
Image.fromarray((255-al*255).astype('uint8')).crop((200,1230,425,1386)).resize((900,624),Image.LANCZOS).save(S+'/t7_tailzoom.png')
Image.fromarray((255-al*255).astype('uint8')).crop((130,380,330,580)).resize((800,800),Image.NEAREST).save(S+'/t7_zoom.png')
