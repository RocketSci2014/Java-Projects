function O1 = lucy_core_ft(O1,Ps_ft,I,bg)
%Lucy_core_ft
%O1: Current guess
%Ps_ft: fftn of the point spread function
%I: image
            %lucy core begin
            O1_ft = fftn(O1);
            I1=real(ifftn(O1_ft.*Ps_ft))+bg; %reblurred image with background
            I1=dot_div2(I,I1);
            I1=real(ifftn(fftn(I1).*conj(Ps_ft))); %if we do not use conj(Ps_ft) but Ps_ft directely, the result is a little distorted. 
            O1=O1.*I1;
       
            %non_negative constrain, it garentee that in all stage, the
            %estimation is positive constrained.
            posi= O1<=0;
            O1(posi)=0; 
end