function result = circTest(g_real, h_real, x, y, z)
    f_size = size(h_real);
    g_size = size(g_real);
    g=zeros(f_size);
    g(1:g_size(1),1:g_size(2),1:g_size(3))=g_real;
    result = circshift(g, [-x, -y, -z]);
end