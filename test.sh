#python scripts/runLoFDroid-multi.py  ../../apk/FanDataICSE2018-before/ Data-fan-0705-NoRV "no" "NoRVType" #Files/FilterAPK.txt
#python scripts/runLoFDroid-multi.py  ../../apk/Empirical500/ Data-500-0705-NoRV "no" "NoRVType"  #Files/FilterAPK.txt

python scripts/runLoFDroid-multi.py  ../../apk/FanDataICSE2018-before/ Data-fan-0705-CG "no" "NoExtendCG" 
python scripts/runLoFDroid-multi.py  ../../apk/FanDataICSE2018-before/ Data-fan-0705-Interact "no" "NoHierarchyAnalysis" 
python scripts/runLoFDroid-multi.py  ../../apk/FanDataICSE2018-before/ Data-fan-0705-v2 "2.3" "no" 
python scripts/runLoFDroid-multi.py  ../../apk/FanDataICSE2018-before/ Data-fan-0705-v12 "12.0" "no" 

python scripts/runLoFDroid-multi.py  ../../apk/Empirical500/ Data-500-0705-CG "no" "NoExtendCG" 
python scripts/runLoFDroid-multi.py  ../../apk/Empirical500/ Data-500-0705-Interact "no" "NoHierarchyAnalysis" 
python scripts/runLoFDroid-multi.py  ../../apk/Empirical500/ Data-500-0705-v2 "2.3" "no" 
python scripts/runLoFDroid-multi.py  ../../apk/Empirical500/ Data-500-0705-v12 "12.0" "no" 

