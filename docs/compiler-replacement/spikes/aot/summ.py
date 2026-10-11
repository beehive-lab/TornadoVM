import collections,statistics,sys
d=collections.defaultdict(list); ok={}
for l in open(sys.argv[1]):
    p=l.split()
    if len(p)==5: d[(p[0],p[1])].append((float(p[3]),float(p[4]))); ok[(p[0],p[1])]=p[2]
for k,v in sorted(d.items()): print('%-16s %-13s ok=%s tc median %6.1f ms  jvm-to-done median %6.1f ms'%(k[0],k[1],ok[k],statistics.median(x[0] for x in v),statistics.median(x[1] for x in v)))
