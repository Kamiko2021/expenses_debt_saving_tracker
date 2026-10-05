const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// This function runs every day at 09:00 AM to check for due dates
exports.dailyDebtReminder = functions.pubsub.schedule('0 9 * * *')
    .timeZone('Asia/Manila') // Adjust to your timezone
    .onRun(async (context) => {
        const db = admin.firestore();
        const now = admin.firestore.Timestamp.now();
        const tomorrow = new admin.firestore.Timestamp(now.seconds + 86400, 0);

        // 1. Get all unpaid debts due within the next 24 hours
        const debtsSnapshot = await db.collection('debts')
            .where('paid', '==', false)
            .where('dueDate', '<=', tomorrow)
            .where('dueDate', '>', now)
            .get();

        if (debtsSnapshot.empty) {
            console.log('No debts due today.');
            return null;
        }

        const promises = [];

        debtsSnapshot.forEach(doc => {
            const debt = doc.data();
            const userId = debt.userId;

            // 2. Fetch the user's FCM token
            const promise = db.collection('users').document(userId).get()
                .then(userDoc => {
                    if (userDoc.exists && userDoc.data().fcmToken) {
                        const token = userDoc.data().fcmToken;

                        // 3. Send the notification
                        const message = {
                            notification: {
                                title: 'Payment Reminder: ' + debt.name,
                                body: 'Your installment of ' + debt.installmentAmount + ' is due soon!'
                            },
                            token: token
                        };

                        return admin.messaging().send(message);
                    }
                    return null;
                });
            promises.push(promise);
        });

        return Promise.all(promises);
    });
