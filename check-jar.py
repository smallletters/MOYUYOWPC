import zipfile
with zipfile.ZipFile('D:/MOYUYOWPC/moyuyo-server/moyuyo-api/target/moyuyo-api-1.0.0.jar') as z:
    for n in z.namelist():
        if 'moyuyo-service' in n and n.endswith('.jar'):
            import io
            with z.open(n) as f:
                nested_data = f.read()
            nested = zipfile.ZipFile(io.BytesIO(nested_data))
            for m in nested.namelist():
                if 'CommunityServiceImpl' in m:
                    print("Found:", m)
                    with nested.open(m) as nf:
                        c = nf.read().decode('utf-8', errors='replace')
                        # 找 setStatus 调用附近
                        idx = c.find('setStatus')
                        if idx >= 0:
                            print('Context:', c[max(0,idx-30):idx+100])
                        break
            break