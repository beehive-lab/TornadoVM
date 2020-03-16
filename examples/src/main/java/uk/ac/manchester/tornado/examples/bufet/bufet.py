######################################################################################################################################################
# Copyright 2016 Konstantinos Zagganas for the Institute for the Management of Information Systems(IMIS) - Athena Research and Innovation Center
# 
# This file is part of BUFET.
# BUFET is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.

# BUFET is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
# For questions regarding this program, please contact
# Konstantinos Zagganas at the following e-mail address:
# zagganas@imis.athena-innovation.gr
######################################################################################################################################################

import sys
import subprocess
import os
import multiprocessing

def isNaturalNumber(s):
    try:
        myf=int(s)
        if myf>0:
        	return True
        else:
        	return False
    except ValueError:
        return False

def isPositiveInteger(s):
    try:
        myf=int(s)
        if myf>=0:
        	return True
       	else:
       		return False
    except ValueError:
        return False

def isFloatNumber(s):
    try:
        float(s)
        return True
    except ValueError:
        return False

def isFloatPositiveNumber(s):
    try:
        myf=float(s)
        if myf>=0:
        	return True
        else:
        	return False
    except ValueError:
        return False

def checkmiRNAFile(mirna_file):
	##
	#Check that the input file exists
	##
	try:
		if os.stat(mirna_file).st_size == 0:
			print('\nError: Input file "' + mirna_file + '" is empty.\n')
			exit(1)
	except OSError:
		print('\nError: Input file "' + mirna_file + '" does not exist.\n')
		exit(1)

def checkInteractionsFile(interactions_file):	
	##
	# Check interactions file
	##
	print('Checking interactions file...')
	try:
		if os.stat(interactions_file).st_size == 0:
			print('\nError: Interactions file "' + interactions_file + '" is empty.\n')
			exit(2)
	except OSError:
		print('\nError: Interactions file "' + interactions_file + '" does not exist.\n')
		exit(2)
	
	f=open(interactions_file)
	i=1
	if altInt=='1':
		for line in f:
			if line[0]=='#':
				continue
			if (line.strip()==""):
				continue
			linetmp=line.strip().split('\t')
			if len(linetmp)>1:
				print('\nError: A wrong delimiter ("\\t") is used in the interactions file in line ' + str(i) +'. Perhaps you are using a wrong file type?')
				exit(2)

			line=line.split('|')
			if len(line)<2:
				print('\nError: There was a problem with the interactions file in line ' + str(i) +'. Please check that the file is using the correct format and try again.')
				exit(2)
			if line[0]=='' or line[0].isspace():
				print('\nError: The miRNA is missing in the interactions file in line ' + str(i) +'. Please check that the file is using the correct format and try again.')
				exit(2)
			if line[1]=='' or line[1].isspace():
				print('\nError: The gene is missing in the interactions file in line ' + str(i) +'. Please check that the file is using the correct format and try again.')
				exit(2)
			i+=1
	else:
		for line in f:
			if (line.strip()==""):
				continue
			if line[0]=='#':
				continue
			if line[0:2]!='>>':
				print('\nError: There was a problem with the miRanda interactions file in line ' + str(i) +'. Please check that the file is the output of miRanda and try again.')
				exit(2)
			line=line.split('\t')
			if len(line)<10:
				print('\nError: There was a problem with the miRanda interactions file in line ' + str(i) +'. Please check that the file is the output of miRanda and try again.')
				exit(2)
			i+=1
	f.close()
	print("OK!")

def checkOntologyFile(ontology_file):
	##
	#Check ontology file
	##
	print('Checking ontology file...')
	try:
		if os.stat(ontology_file).st_size == 0:
			print('\nError: Ontology file "' + ontology_file + '" is empty.\n')
			exit(3)
	except OSError:
		print('\nError: Ontology file "' + ontology_file + '" does not exist.\n')
		exit(3)

	f=open(ontology_file)
	i=1
	nameMissing=False
	if altOnt=='1':
		for line in f:
			if (line.strip()==""):
				continue
			if line[0]=='#':
				continue
			linetmp=line.strip().split('\t')
			if len(linetmp)>1:
				print('\nError: The wrong delimiter ("\\t") is used in the ontology file in line ' + str(i) +'. Perhaps you are using a wrong file type?')
				exit(2)
			line=line.strip().split('|')
			if len(line)<3 :
				print('\nError: There was a problem with the ontology file in line ' + str(i) +'. Please check that the file is using the correct format and try again.')
				exit(3)
			if line[0]=='' or line[0].isspace():
				print('\nError: The gene is missing in the ontology file in line ' + str(i) +'. Please check that the file is using the correct format and try again.')
				exit(3)
			if line[1]=='' or line[1].isspace():
				print('\nError: The term is missing in the ontology file in line ' + str(i) +'. Please check that the file is using the correct format and try again.')
				exit(3)
			if line[2]=='' or line[2].isspace():
				nameMissing=True

			i+=1
	else:
		for line in f:
			if (line.strip()==""):
				continue
			if line[0]=='#':
				continue
			line=line.split('\t')
			if len(line)<7:
				print('\nError: There was a problem with the Ensembl ontology file in line ' + str(i) +'. Please check the file and try again.')
				exit(3)
			i+=1
	f.close()
	if nameMissing:
			print('Warning: Some ontology terms are missing a name! Execution will continue normally...')
	else:
		print("OK!")
	
def checkSynonymsFile(synonyms_file):
	##
	# Check synonyms file
	##
	print('Checking synonyms file...')
	try:
		if os.stat(synonyms_file).st_size == 0:
			print('\nError: Synonyms file "' + synonyms_file + '" is empty.\n')
			exit(4)
	except OSError:
		print('\nError: Synonyms file "' + synonyms_file + '" does not exist.\n')
		exit(4)
	
	f=open(synonyms_file)
	i=1
	for line in f:
		if (line.strip()==""):
				continue
		if line[0]=='#':
			continue
		line=line.split('\t')
		if len(line)<5:
			print('\nError: There was a problem with the synonyms file in line ' + str(i) +'. Please check the file and try again.')
			exit(4)
		i+=1
	f.close()
	print("OK!")

#Print help message
def printOptions():
	print('Usage:\n\t\tpython bufet.py [options]\n\nMandatory arguments:\n')
	print('\t-miRNA <filePath>: path to the miRNA group file')
	print('\t-interactions filePath>: path to the interactions file')
	print('\t-ontology <filePath>: path to the ontology file')
	print('\t-synonyms <filePath>: path to the synonyms file\n')
	print('Additional options:\n')
	print('\t-iterations: number of random permutations')
	print('\t-output <filePath>: path to the output file (overwritten if it exists)')
	print('\t-processors: number of threads to use for calculations')
	print('\t-species: "human" or "mouse"')
	print('\t-miFree: miRanda free energy (valid only if the --miRanda flag is invoked)')
	print('\t-miScore: miRanda free energy (valid only if the --miRanda flag is invoked)\n')
	print('\t--miRanda: use interactions file from miRanda run')
	print('\t--ensGO: use ontology file downloaded from Ensembl')
	print('\t--disable-file-check: (quicker but not recommended) disable all file validations.')
	print('\t--disable-interactions-check: (quicker but not recommended) disable existence and file format validation for the interactions file.')
	print('\t--disable-ontology-check: (quicker but not recommended) disable existence and file format validation for the ontology file.')
	print('\t--disable-synonyms-check: (quicker but not recommended) disable existence and file format validation for the synonyms file.')
	print('\t--help: print this message and exit')

commandLine=sys.argv
available_species={'human':'9606','mouse':'10090'}
options = {}

options['-processors'] = str(max(1,multiprocessing.cpu_count() - 1))
options['-iterations'] = '10000'
options['-miRNA'] = ''
options['-ontology'] = ''
options['-interactions'] = ''
options['-output'] = 'output.txt'
options['-species'] = 'human'
options['-synonyms'] = ''
options['-miRanda']='no'
options['-ensGO']='no'
options['-miScore'] = '155.0'
options['-miFree'] = '-20.0'
options['-disable-file-check']='no'
options['-help']='no'
options['-disable-interactions-check']='no'
options['-disable-synonyms-check']='no'
options['-disable-ontology-check']='no'

#Read command line arguments
i=1
while i <len(commandLine):
	if commandLine[i][0:2]=='--':
		if commandLine[i][1:] in options:
			options[commandLine[i][1:]]='yes'
			i+=1
			continue
		else:
			print ('\nError: unrecognized option: ' + commandLine[i] + '\nFor the list of options, run the script with -h or --help.')
			exit(7)
	elif commandLine[i][0]=='-':
		if (commandLine[i]=='-h') or (commandLine[i]=='-help'):
			options['-help']='yes'
			break
		if commandLine[i] not in options:
			print ('\nError: unrecognized option: ' + commandLine[i] + '\nFor the list of options, run the script with -h or --help.')
			exit(7)
		if i+1>=len(commandLine):
			print('\nError: Argument ' + commandLine[i] + ' is missing a value.')
			exit(7)
		if i>len(commandLine):
			break
		if (commandLine[i+1][0]=='-') and (isFloatNumber(commandLine[i+1])==False):
			print('\nError: Argument ' + commandLine[i] + ' is missing a value.')
			exit(7)
		options[commandLine[i]]=commandLine[i+1]
		i+=2
	else:
		print ('\nError: unrecognized option: ' + commandLine[i] + '\nFor the list of options, run the script with -h or --help.')
		i+=1
		exit(7)

if options['-help']=='yes':
	printOptions()
	exit(0)

if options['-miRanda']=='no':
	altInt='1'
else:
	altInt='0'

if options['-ensGO']=='no':
	altOnt='1'
else:
	altOnt='0'

#Find script path, which must be the same as the executable
script_path=os.path.dirname(os.path.realpath(__file__))
executable=script_path+'/bufet.bin'

if not os.path.exists(executable):
	print('\nError: Executable file "bufet.bin" not in folder "' + os.path.abspath('.') + '".\nPlease run "make" to compile the C++ core or move the binary inside the folder.\n')
	exit(5)

#Check that parameters are valid
if options['-miRNA']=='':
	print('\nError: No input miRNA file specified!')
	exit(1)
if options['-interactions']=='':
	print('\nError: No interactions file specified!')
	exit(1)
if options['-ontology']=='':
	print('\nError: No ontology file specified!')
	exit(1)
if options['-synonyms']=='':
	print('\nError: No synonyms file specified!')
	exit(1)


options['-species']=options['-species'].strip('"')
if options['-species'] not in available_species:
	print('\nError: Unrecognized species. Available options: \n"human"\n"mouse"')
	exit(10)

if (isNaturalNumber(options['-iterations'])==False):
	print('\nError: The number of iterations is not valid. Please provide a non-negative and non-zero number of iterations')
	exit(9)
if (isNaturalNumber(options['-processors'])==False):
	print('\nError: The number of processors is not valid. Please provide a non-negative and non-zero number of iterations')
	exit(9)

if altInt=='0':
	if isFloatPositiveNumber(options['-miScore'])==False:
		print('\nError: miRanda score is not a valid numerical value. Please provide non-negative score value.')
		exit(9)
	if isFloatNumber(options['-miFree'])==False:
		print('\nError: Free energy is not a numerical value. Please provide a valid free energy value.')
		exit(9)



#Check files if not disabled
if options['-disable-file-check']=='no':
	checkmiRNAFile(os.path.abspath(options['-miRNA']))
	if options['-disable-interactions-check']=='no':
		checkInteractionsFile(os.path.abspath(options['-interactions']))
	else:
		print('Warning: Interactions file validation has been disabled.')
	if options['-disable-ontology-check']=='no':
		checkOntologyFile(os.path.abspath(options['-ontology']))
	else:
		print('Warning: Ontology file validation has been disabled.')
	if options['-disable-synonyms-check']=='no':
		checkSynonymsFile(os.path.abspath(options['-synonyms']))
	else:
		print('Warning: Synonyms file validation has been disabled.')
else:
	print('Warning: File validation is disabled.')

#run script
print('Starting BUFET')
return_code=subprocess.call([executable,options['-interactions'],options['-output'],options['-miRNA'],options['-ontology'], options['-iterations'], options['-processors'],options['-synonyms'],options['-miFree'],options['-miScore'],available_species[options['-species']],altInt,altOnt])



