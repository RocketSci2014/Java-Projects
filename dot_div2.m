function dH=dot_div2(varargin)
%Purpose: DOT_DIV2 get the value of h2/h1. %if the denominator is 0, it is divided
%by a replaced value. 
%
%Syntax: 
%   dH=DOT_DIV(h2, h1) 
%   dH=DOT_DIV(h2, h1, threshold) 
%
%Arguments:
%   
%
%Description:
%   dH=DOT_DIV(h2, h1) is same as dH=h2./h1. This function can eliminate the case of divided by zero.
%   h1 and h2 are assumed to be the same size.
%   dH=DOT_DIV(h2, h1, threshold) will take care the value which is smaller
%   than threshold
%
%Programmer Comments:
%   None
%
%Algorithm
%   None
%
%Limitations: 
%   None
%
%Class Fields:
%   None
%
%Updated:  
%Date: 10/16/2002
%Name: Xuming Lai
%What: Created
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

h2=varargin{1};
h1=varargin{2};

%assume h1 and h2 has the same size
h1_size=size(h1);
if ndims(h1)<3
    h1_size(3)=1;
end

dH=zeros(h1_size);
switch nargin
case 2
    p=find(abs(h1)==0);
    h1(p)=eps; %small value in matlab
    dH=h2./h1;
    
case 3
    threshold=varargin{3};
    p=find(abs(h1)<threshold);
    h1(p)=threshold;
    dH=h2./h1;
otherwise
    error('The number of the input is not right.');
end