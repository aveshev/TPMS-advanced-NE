# Rebuild line art as clean anti-aliased strokes: find each line's centerline at 4x, then redraw it
# with a width that follows how strong the source line was (keeps main vs detail line hierarchy)
from PIL import Image, ImageFilter
import numpy as np
from skimage.morphology import skeletonize
from scipy import ndimage as ndi
K=4
def restroke(alpha4x, w_min, w_max):
    """alpha4x: float 0..1 image at 4x target, returns float 0..1 image at 4x"""
    sm=ndi.gaussian_filter(alpha4x, 1.2)
    mask=sm>0.22
    skel=skeletonize(mask)
    # strength of the source line around each skeleton pixel -> stroke width
    strength=ndi.maximum_filter(alpha4x, size=5)
    radius=np.where(skel,(w_min+(w_max-w_min)*np.clip(strength,0,1))*K/2,0)
    dist,(iy,ix)=ndi.distance_transform_edt(~skel,return_indices=True)
    r=radius[iy,ix]
    return np.clip(r-dist+0.5,0,1)
def down(a4):
    return np.array(Image.fromarray((a4*255).astype(np.uint8)).resize((a4.shape[1]//K,a4.shape[0]//K),Image.BOX)).astype(float)

def restroke_smooth(alpha4x, width, mask_sigma=0.8, mask_level=0.3, smooth_sigma=1.6, spur=10):
    """Uniform-width strokes along the lines' centerlines, with short skeleton spurs pruned and the
    result smoothed so centerline wobble and thickness changes don't read as jagged edges"""
    sm=ndi.gaussian_filter(alpha4x, mask_sigma*K/4*2)
    skel=skeletonize(sm>mask_level)
    # prune spurs: repeatedly drop end points, then grow the surviving skeleton back along itself
    kern=np.ones((3,3)); kern[1,1]=0
    s=skel.copy()
    for _ in range(spur):
        n=ndi.convolve(s.astype(int),kern,mode='constant')
        s&=~(n<=1)
    for _ in range(spur):
        s|=skel&ndi.binary_dilation(s,structure=np.ones((3,3)))
    dist=ndi.distance_transform_edt(~s)
    a=np.clip(width*K/2-dist+0.5,0,1)
    # round off wobble: blur then re-harden with a soft edge
    b=ndi.gaussian_filter(a,smooth_sigma)
    t=np.clip((b-0.3)/0.4,0,1)
    return t*t*(3-2*t)
