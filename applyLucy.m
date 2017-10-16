function [x1, x2, x3] = applyLucy(I, P, bg, N)
    %the initial value of the image matrix 
    I_first = I - bg;
    
    %positive constrain, the original image has to be positive. 
    I=max(I,0); %image without negative value
    I_first=max(I_first,0); % first guest with positive constraint and background.
    P=max(P,0); %psf no negative value

    %garentee unit energy of psf
    P=P/sum(P(:));

    I_size=size(I);
    P_size=size(P);

    %assume I has bigger size and shifting the P matrix.
    Ps=zeros(I_size);
    P_start=floor(P_size/2); %starting of center
    Ps(1:P_size(1),1:P_size(2), 1:P_size(3))=P;
    Ps=circshift(Ps,-1*P_start);
    Ps_ft=fftn(Ps);

    %Agard   
    O1=I_first;
    for k=1:N
        O1_ft=fftn(O1);
        I1=real(ifftn(O1_ft.*Ps_ft));
        O1=O1.*dot_div2(I,I1);
        %non_negative constrain
        O1(O1<=0)=0;
    end
     x1 = O1; 

    %RichardsonLucy
    O1=I_first;
    for k=1:N
        O1=lucy_core_ft(O1,Ps_ft,I,bg);
    end
    x2 = O1;   
    
   %AcceleratedRichardsonLucy

   %subscript n for next interation, subscript c for current
   %interation, subscript p for previous section
   %X: interated point
   %Y: predicted point
   %first interation
   X_c=I_first;
   Y_c=X_c; 
   X_n=lucy_core_ft(Y_c,Ps_ft,I,bg);
   %Y_n=X_c;
   g{2}=X_n-Y_c; %g{1}:g(k-1) (latest one), g{2}:g(k-2) (older one);
   %second interation
   %update the result from last interation
   X_c=X_n;
   Y_c=X_c; 
   X_n=lucy_core_ft(Y_c,Ps_ft,I,bg);
   g{1}=X_n-Y_c;
   
   %start of the third interation
   k=3;
   while k<=N
       %update result from last interation
       X_p=X_c;
       X_c=X_n;

       alpha=sum(sum(sum(g{1}.*g{2})))/(sum(sum(sum(g{2}.*g{2})))+eps); %accelaration factor
       %eps is added in denominator to prevent it from dividing by zeros
       if alpha>1 || alpha<0
           alpha=0; %the acceration factor is not acceptable and no acceration is done
       end
       h=X_c-X_p;
       %Y_c=X_c+alpha*h;
       Y_c=max(X_c+alpha*h,0);  %positive constraint
       X_n=lucy_core_ft(Y_c,Ps_ft,I,bg);
       %update g
       g{2}=g{1};
       g{1}=X_n-Y_c;
       %update counter
       k=k+1;
   end
   O1=X_n;
   x3 = O1;
end