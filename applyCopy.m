function [x1, x2, x3, x4] = applyCopy(g_real, h_real, r)
g_size = size(g_real);
h_size = size(h_real);
f_size = zeros(1, 3);
f_size(1) = h_size(1);
f_size(2) = h_size(2);

if g_size(3) > h_size(3)
    f_size(3) = g_size(3);
else 
    f_size(3) = h_size(3);
end
h=zeros(f_size);
h(1:h_size(1),1:h_size(2),1:h_size(3))=h_real;  
g_start=floor(g_size/2);
g=zeros(f_size);
g(1:g_size(1),1:g_size(2),1:g_size(3))=g_real(:, :, :);
g=circshift(g,-1*g_start);

%Fourier transform of g and resultant Wiener filter
g=g/sum(g(:));  %make the overall sum unit
g_real_ft=fftn(g, [f_size(1) f_size(2) f_size(3)]);

%if the input sum of psf is 1. It does not make any difference. 
%we assume the sum of psf should be 1 during the convolution process. 
max_g=max(max(max(abs(g_real_ft)))); %This value equals total intensity in the psf and 
                                     %PSF is normalized by this value to
                                     %make sum(psf(:))==1

h_real_ft=fftn(h, [f_size(1) f_size(2) f_size(3)]);

%% %%

%simple regularized least square
numor1=conj(g_real_ft/max_g).*h_real_ft;
denor1=abs(g_real_ft/max_g).^2+r;
%estimated object
x_ft1=numor1./denor1;
x1=real(ifftn(x_ft1,f_size,'symmetric'));

%regularized least square with some treatment similar to matlab
%%%%%%
%This method is no good.
%%%%%%        
numor2=conj(g_real_ft/max_g).*h_real_ft;
denor2=abs(g_real_ft/max_g).^2+r;

%for rlsq, the oh_tiny is about 5e-8 to 5e-9 for rlsq 
%for weight, the oh_tiny 
oh_tiny=5e-8; %smaller value will give clearer result, but It will give more side effect.
oh_small=max(max(max(numor2)))*oh_tiny;%? error
po_small=find(abs(denor2)<oh_small);
signof_small=2*(real(denor2(po_small))>0)-1;
denor2(po_small)=signof_small*oh_small;

%estimated object
x_ft2=numor2./denor2;
x2 =real(ifftn(x_ft2,f_size,'symmetric'));

%so far it seems gives best results
numor3=conj(g_real_ft/max_g).*h_real_ft.*abs(g_real_ft/max_g).^2;
denor3=abs(g_real_ft/max_g).^4+r;

%estimated object
x_ft3=numor3./denor3;
x3 =real(ifftn(x_ft3,f_size,'symmetric'));

%It uses the distance as a weight
factor = zeros(f_size);
for i=1:f_size(1)
    for j=1:f_size(2)
        for k=1:f_size(3) %factor is nothing but the distance
            factor(i,j,k)=(abs(i-round(f_size(1)/2)).^2+abs(j-round(f_size(2)/2)).^2+abs(k-round(f_size(3)/2)).^2)/(f_size(1)^2+f_size(2)^2+f_size(3)^2);
            %factor(i,j,k)=abs(i-round(f_size(1)/2))/f_size(1)+abs(j-round(f_size(2)/2))/f_size(2)+abs(k-round(f_size(3)/2))/f_size(3);
        end
    end
end
factor=ifftshift(factor)*2*pi;        
numor4=conj(g_real_ft/max_g).*h_real_ft;
denor4=abs(g_real_ft/max_g).^2+2*r*factor;

%estimated object
x_ft4=numor4./denor4;
x4=real(ifftn(x_ft4,f_size,'symmetric'));
end