

import xlsxwriter, os

num_client = 100
num_messages = 60
all_clients = []


cd = os.getcwd()
path  = cd + "/temp/"

for i in range(num_client):
	try:
		f = open(path + 'client'+str(i)+'.txt','r')
	except IOError:
		print "file: client" + str(i)+'.txt does not exist'
		num_client = num_client - 1
		continue
	entries = []

	# pre-populated entries
	# a list of [mesID, send time, recv t1, recv t2, ...]

	for mesnum in range(num_messages):
		entries.insert(mesnum, [mesnum, 0.0])

	l = f.readline()


	while l != '':
		# print l
		content = l.split(':')
		# print content

		header = content[0]
		# print "header is " + header

		if header == 'PAINT':
			coor = content[1].split(' ')
			clientID = int(coor[0])
			MessageID = int(coor[1])
			# print "paint content: "
			# print coor

			if clientID == i:
				receive_time = f.readline()
				# print receive_time
				rec = (receive_time.split(':')[1]).strip(' }\n')
				f.readline()
				send_time = f.readline()
				# print send_time
				sdt = (send_time.split(':')[1]).strip(' }\n')

				entries[MessageID] = [MessageID, float(sdt)]

				l = send_time

			else:
				f.readline()
				f.readline()
				l = f.readline()
		else:
			f.readline()
			f.readline()
			l = f.readline()

		l = f.readline()

	f.close()

	all_clients.insert(i, entries)



# for i in range(num_client):
# 	print '#client ', i
# 	print all_clients[i]

for i in range(num_client):
	
	try:
		f = open(path + 'client'+str(i)+'.txt','r')
	except IOError:
		print "file: client" + str(i)+'.txt does not exist'
		continue

	l = f.readline()


	while l != '':
		content = l.split(':')
		# print content

		header = content[0]
		# print "header is " + header

		if header == 'PAINT':
			coor = content[1].split(' ')

			clientID = int(coor[0])
			messageID = int(coor[1])

			# print "PAINT has clientID: ", clientID, " and messageID: ", messageID

			receive_time = f.readline()
			# print receive_time
			rec = (receive_time.split(':')[1]).strip(' ,}\n')
			# print rec
			f.readline()
			l = f.readline()

			# print clientID
			# print messageID
			if clientID < num_client:
				all_clients[clientID][messageID].insert(2+i, float(rec))

		else:
			l = f.readline()
			l = f.readline()
			l = f.readline()

		l = f.readline()


	f.close()


# for i in range(num_client):
# 	print '#client ', i
# 	print all_clients[i]



workbook = xlsxwriter.Workbook('paint.xlsx')
bold = workbook.add_format({'bold': True})

for i in range(num_client):
	print "Processing Client #", i
	row = 0
	col = 0
	
	worksheet = workbook.add_worksheet('client' + str(i))
	data = all_clients[i]
	worksheet.write(row, col, 'Message Number', bold)
	worksheet.write(row, col + 1, 'Sent Time', bold)
	for cn in range(num_client):
		worksheet.write(row, col+2+cn, 'Client ' +str(cn)+' RevTime', bold)

	row = row + 1

	for e in data:
		for field in e:
			worksheet.write_number(row, col, field)
			col = col + 1
		row = row + 1
		col = 0

workbook.close()

# For each client, there is an excel sheet.
# MessageID		SendTime		Client 1 Receive Time 		Client 2 Receive Time 		...
#     1				x
#     2				y
#     3				z
#     . 			.
#     . 			.
