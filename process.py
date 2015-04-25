

import xlsxwriter

num_client = 50
all_clients = []


for i in range(num_client):
	f = open('client'+str(i)+'.txt','r')
	entries = []

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

				entries.insert(MessageID, [MessageID, float(sdt)])

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




for i in range(num_client):
	f = open('client'+str(i)+'.txt','r')
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

			receive_time = f.readline()
			# print receive_time
			rec = (receive_time.split(':')[1]).strip(' ,}\n')
			# print rec
			f.readline()
			l = f.readline()

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
