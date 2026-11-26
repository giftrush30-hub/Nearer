import SwiftUI

struct NearbyHomeView: View {
    @EnvironmentObject private var model: NearbyHomeModel
    private let navy = Color(red: 0.035, green: 0.075, blue: 0.16)
    private let blue = Color(red: 0.10, green: 0.39, blue: 0.86)
    private let gold = Color(red: 0.96, green: 0.73, blue: 0.22)

    var body: some View {
        ZStack {
            LinearGradient(colors: [navy, Color(red: 0.045, green: 0.12, blue: 0.25), navy],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
                .ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header
                    identityCard
                    discoveryCard
                    peerList
                    verificationQueue
                    messagingPanel
                    privacyNote
                }
                .padding(20)
            }
        }
        .preferredColorScheme(.dark)
    }

    private var header: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 17).fill(blue.gradient)
                    .frame(width: 54, height: 54)
                Image(systemName: "point.3.connected.trianglepath.dotted")
                    .font(.system(size: 25, weight: .semibold))
                    .foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text("NeaRer").font(.system(size: 29, weight: .bold, design: .rounded))
                Text("NeaRer You, NeaRer Everything")
                    .font(.caption).foregroundStyle(.white.opacity(0.65))
            }
            Spacer()
            Circle().fill(gold).frame(width: 8, height: 8)
        }
        .padding(.top, 12)
    }

    private var identityCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("YOUR NEARBY ID").font(.caption2.weight(.bold)).tracking(1.5)
                .foregroundStyle(gold)
            HStack {
                Text(model.peerID).font(.system(size: 23, weight: .bold, design: .monospaced))
                    .tracking(2)
                Spacer()
                Image(systemName: "checkmark.seal.fill").foregroundStyle(gold).font(.title2)
            }
            Text("Your identity stays on this device. Nearby names are confirmed only after secure peer verification.")
                .font(.footnote).foregroundStyle(.white.opacity(0.65))
        }
        .padding(18)
        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(.white.opacity(0.10)))
    }

    private var discoveryCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Nearby discovery").font(.title3.bold())
                    Text("Find NeaRer devices around you").font(.footnote).foregroundStyle(.white.opacity(0.6))
                }
                Spacer()
                Image(systemName: "dot.radiowaves.left.and.right")
                    .font(.title2).foregroundStyle(gold)
            }
            HStack(spacing: 10) {
                Circle().fill(model.isSearching ? Color.green : .gray).frame(width: 8, height: 8)
                Text(model.status).font(.footnote).foregroundStyle(.white.opacity(0.75))
            }
            Button {
                if model.isSearching { model.stopDiscovery() } else { model.startDiscovery() }
            } label: {
                HStack {
                    Image(systemName: model.isSearching ? "stop.fill" : "dot.radiowaves.left.and.right")
                    Text(model.isSearching ? "Stop searching" : "Find nearby devices")
                }
                .font(.headline)
                .foregroundStyle(navy)
                .frame(maxWidth: .infinity).padding(.vertical, 15)
                .background(LinearGradient(colors: [gold, Color(red: 1, green: 0.84, blue: 0.43)],
                                           startPoint: .leading, endPoint: .trailing),
                            in: RoundedRectangle(cornerRadius: 15))
            }
            .buttonStyle(.plain)
        }
        .padding(18)
        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(.white.opacity(0.10)))
    }

    private var peerList: some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack {
                Text("Devices around you").font(.title3.bold())
                Spacer()
                Text("\(model.peers.count)").font(.caption.bold()).foregroundStyle(gold)
            }
            if model.peers.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "dot.radiowaves.left.and.right")
                        .font(.system(size: 30)).foregroundStyle(.white.opacity(0.3))
                    Text("Your nearby space is quiet")
                        .font(.subheadline.weight(.semibold))
                    Text("Keep NeaRer open while another device searches.")
                        .font(.caption).foregroundStyle(.white.opacity(0.55))
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity).padding(.vertical, 28)
                .background(.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 18))
            } else {
                ForEach(model.peers) { peer in
                    HStack(spacing: 12) {
                        Circle().fill(blue.gradient).frame(width: 42, height: 42)
                            .overlay(Image(systemName: "iphone.radiowaves.left.and.right").foregroundStyle(.white))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Nearby NeaRer device").font(.subheadline.bold())
                            Text("Discovered over local peer networking").font(.caption).foregroundStyle(.white.opacity(0.55))
                        }
                        Spacer()
                        Button("Connect") { model.connect(to: peer) }
                            .font(.caption.bold()).foregroundStyle(gold)
                    }
                    .padding(13).background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 16))
                }
            }
        }
    }

    private var verificationQueue: some View {
        VStack(alignment: .leading, spacing: 13) {
            if !model.pendingApprovals.isEmpty {
                Text("VERIFY THIS DEVICE")
                    .font(.caption2.weight(.bold)).tracking(1.5)
                    .foregroundStyle(gold)
                ForEach(model.pendingApprovals) { pending in
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 10) {
                            Image(systemName: "checkmark.shield.fill")
                                .font(.title2).foregroundStyle(gold)
                            VStack(alignment: .leading, spacing: 3) {
                                Text("Peer authenticated").font(.subheadline.bold())
                                Text("NeaRer ID: \(pending.peerID)")
                                    .font(.caption).foregroundStyle(.white.opacity(0.65))
                            }
                        }
                        Text("Compare this six-digit code with the other device. Approve only when both screens show the same code.")
                            .font(.caption).foregroundStyle(.white.opacity(0.65))
                        Text(pending.verificationCode)
                            .font(.system(size: 34, weight: .bold, design: .monospaced))
                            .tracking(5)
                            .foregroundStyle(gold)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                        Text("Signing-key fingerprint (additional check)")
                            .font(.caption2.weight(.semibold)).foregroundStyle(.white.opacity(0.55))
                        Text(pending.fingerprint)
                            .font(.system(size: 11, weight: .semibold, design: .monospaced))
                            .textSelection(.enabled)
                            .foregroundStyle(gold)
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(.black.opacity(0.22), in: RoundedRectangle(cornerRadius: 10))
                        HStack(spacing: 10) {
                            Button("Decline") { model.rejectPeer(pending) }
                                .font(.subheadline.bold())
                                .foregroundStyle(.white.opacity(0.8))
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                            Button("Approve device") { model.approvePeer(pending) }
                                .font(.subheadline.bold())
                                .foregroundStyle(navy)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(gold, in: RoundedRectangle(cornerRadius: 12))
                        }
                    }
                    .padding(16)
                    .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(gold.opacity(0.35)))
                }
            }
            if !model.securePeerIDs.isEmpty {
                Text("TRUSTED NEARBY DEVICES")
                    .font(.caption2.weight(.bold)).tracking(1.5)
                    .foregroundStyle(gold)
                ForEach(model.securePeerIDs.sorted(), id: \.self) { trustedID in
                    HStack(spacing: 10) {
                        Image(systemName: "lock.shield.fill").foregroundStyle(gold)
                        Text(trustedID).font(.subheadline.monospaced().bold())
                        Spacer()
                        Text("Verified").font(.caption.bold()).foregroundStyle(.green)
                    }
                    .padding(13)
                    .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
                }
            }
        }
    }

    private var messagingPanel: some View {
        Group {
            if let remotePeerID = model.securePeerIDs.sorted().first {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Image(systemName: "lock.fill").foregroundStyle(gold)
                        Text("Private chat").font(.title3.bold())
                        Spacer()
                        Text("E2E encrypted").font(.caption2.bold()).foregroundStyle(.green)
                    }
                    Text("Connected peer: \(remotePeerID)")
                        .font(.caption).foregroundStyle(.white.opacity(0.6))
                    let conversation = model.messages.filter { $0.peerID == remotePeerID }.suffix(40)
                    if conversation.isEmpty {
                        Text("Your secure conversation starts here.")
                            .font(.footnote).foregroundStyle(.white.opacity(0.55))
                            .frame(maxWidth: .infinity).padding(.vertical, 18)
                    } else {
                        VStack(spacing: 9) {
                            ForEach(Array(conversation)) { message in
                                HStack {
                                    if message.senderID == model.peerID { Spacer(minLength: 36) }
                                    Text(message.text)
                                        .font(.subheadline)
                                        .foregroundStyle(.white)
                                        .padding(12)
                                        .background(
                                            message.senderID == model.peerID ? blue.opacity(0.85) : .white.opacity(0.10),
                                            in: RoundedRectangle(cornerRadius: 15)
                                        )
                                    if message.senderID != model.peerID { Spacer(minLength: 36) }
                                }
                            }
                        }
                    }
                    HStack(spacing: 9) {
                        TextField("Write a message…", text: $model.messageDraft, axis: .vertical)
                            .lineLimit(1...4)
                            .padding(12)
                            .background(.black.opacity(0.22), in: RoundedRectangle(cornerRadius: 13))
                            .foregroundStyle(.white)
                        Button {
                            model.sendMessage(to: remotePeerID)
                        } label: {
                            Image(systemName: "arrow.up")
                                .font(.headline.bold())
                                .foregroundStyle(navy)
                                .frame(width: 44, height: 44)
                                .background(gold, in: Circle())
                        }
                        .disabled(model.messageDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        .opacity(model.messageDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.45 : 1)
                    }
                    Text("Messages are encrypted in transit and stored encrypted on this device. Delivery receipts, offline outbox retry and group messaging are not yet implemented.")
                        .font(.caption2).foregroundStyle(.white.opacity(0.5))
                }
                .padding(18)
                .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 22))
                .overlay(RoundedRectangle(cornerRadius: 22).stroke(.green.opacity(0.25)))
            }
        }
    }

    private var privacyNote: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "lock.shield.fill").foregroundStyle(gold)
            Text("NeaRer uses local networking for nearby connections. Internet access is not required for this local discovery route. Messages remain unavailable until the shared cryptographic handshake and durable messaging store are integrated.")
                .font(.caption).foregroundStyle(.white.opacity(0.55))
        }
        .padding(.vertical, 8)
    }
}
