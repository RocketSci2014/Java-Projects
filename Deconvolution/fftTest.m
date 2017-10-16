function [fftReal, fftImag, ifftReal, ifftImag] = fftTest(realIn, imagIn)
    h = size(realIn, 1);
    w = size(realIn, 2);
    n = size(realIn, 3);
    fftSize = [h, w, n];
    input = realIn + 1i * imagIn;
    fftResult = fftn(input, fftSize);
    fftReal = real(fftResult);
    fftImag = imag(fftResult);
    ifftResult = ifftn(fftResult, fftSize);
    ifftReal = real(ifftResult);
    ifftImag = imag(ifftResult);
end