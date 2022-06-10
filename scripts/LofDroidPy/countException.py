import json

version="9.0"
path = "C:\\Users\\yanjw\\programs\\LoFDroid-develop\\Files\\Exceptions\\" +version +".json"
# 读取文件数据
with open(path, "r",encoding='utf-8') as f:
    row_data = json.load(f)['exceptions']

exception=0
ParameterOnly = 0
FieldOnly = 0
ParaAndField = 0
OverrideMissing = 0
unknownRV = 0
exceptionTypeSet=set()
methodSet=set()
# 读取每一条json数据
for d in row_data:
    exception+=1
    if('relatedVarType' in d.keys()):
        if(d['relatedVarType'] == 'ParameterOnly'):
            ParameterOnly+=1
        if(d['relatedVarType'] == 'FieldOnly'):
            FieldOnly+=1
        if (d['relatedVarType'] == 'ParaAndField'):
            ParaAndField += 1
        if (d['relatedVarType'] == 'OverrideMissing'):
            OverrideMissing += 1
    else:
        unknownRV+=1
    exceptionTypeSet.add(d['type'])
    methodSet.add(d['method'])

print("version\t" , "android-"+str(version))
print("exception\t" , exception)
print("exception type\t" , len(exceptionTypeSet))
print("method with exception\t" , len(methodSet))
print("ParameterOnly\t" , ParameterOnly)
print("FieldOnly\t" , FieldOnly)
print("ParaAndField\t" , ParaAndField)
print("noRV\t" , OverrideMissing)
print("unknownRV\t" , unknownRV)
