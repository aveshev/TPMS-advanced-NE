from PIL import Image, ImageDraw
import numpy as np, sys
S=sys.argv[1]
src=Image.open('/tmp/claude-0/-home-user-TPMS-advanced-NE/43403585-3f5f-5e9e-ad58-22b6693c915c/images/3.webp').convert('RGBA')
A=np.array(src.getchannel('A')).copy()
A[1455:1885,0:173]=0; A[1455:1885,632:804]=0
K=4
m=Image.new('L',(804*K,2000*K),0); d=ImageDraw.Draw(m)
def outline(x0,x1,y0,y1,cap=48,n=4.0):
    # Superellipse end caps: flat-ish, slightly domed ends with soft corners, like the original fender
    cx,hw=(x0+x1)/2,(x1-x0)/2
    top=[(cx+hw*np.sign(np.cos(t))*abs(np.cos(t))**(2/n), y0+cap-cap*abs(np.sin(t))**(2/n)) for t in np.linspace(np.pi,0,80)]
    bot=[(cx+hw*np.sign(np.cos(t))*abs(np.cos(t))**(2/n), y1-cap+cap*abs(np.sin(t))**(2/n)) for t in np.linspace(0,-np.pi,80)]
    return top+bot+[top[0]]
def crease(xi,sgn,y0,y1):
    # Crease running near the inner edge, tapering into the edge at both ends
    ys=np.linspace(y0+20,y1-20,120); pts=[]
    for y in ys:
        u=min(y-(y0+20),(y1-20)-y)/40
        off=7+8*min(1,u)**0.5
        pts.append((xi-sgn*off,y))
    return pts
y0,y1=1462,1878
for x0,x1,xi,sgn in ((4,172,172,1),(632,800,632,-1)):
    d.line([(x*K,y*K) for x,y in outline(x0,x1,y0,y1)],fill=255,width=3*K,joint='curve')
    d.line([(x*K,y*K) for x,y in crease(xi,sgn,y0,y1)],fill=255,width=6,joint='curve')
f=np.array(m.resize((804,2000),Image.LANCZOS)); A=np.maximum(A,f)
out=Image.merge('RGBA',[Image.new('L',(804,2000),0)]*3+[Image.fromarray(A)])
W,H=625,1386; s=H/2000
a=out.getchannel('A').resize((round(804*s),H),Image.LANCZOS)
canvas=Image.new('L',(W,H),0); canvas.paste(a,((W-a.size[0])//2,0))
Image.merge('RGBA',[Image.new('L',(W,H),0)]*3+[canvas]).save('feature/main/src/main/res/drawable-nodpi/schema_delta_three_wheeler_top_view.webp',lossless=True,quality=100,method=6)
al=np.array(canvas)/255
Image.fromarray((255-al*255).astype('uint8')).save(S+'/delta_light.png')
Image.fromarray((20+al*210).astype('uint8')).save(S+'/delta_preview.png')
bg=Image.new('RGBA',out.size,(255,255,255,255)); bg.alpha_composite(out)
bg.crop((0,1400,804,1960)).convert('RGB').save(S+'/delta_rear_zoom.png')
