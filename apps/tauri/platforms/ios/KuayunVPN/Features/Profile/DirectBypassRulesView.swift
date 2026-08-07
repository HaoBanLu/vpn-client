import SwiftUI

struct DirectBypassRulesView: View {
    @State private var rules: [DirectBypassRule] = DirectBypassRuleStore.load()
    @State private var newValue = ""
    @State private var newType: DirectBypassRuleType = .domainSuffix
    @State private var error: String?

    var body: some View {
        List {
            Section("新增规则") {
                Picker("类型", selection: $newType) {
                    ForEach(DirectBypassRuleType.allCases) { t in
                        Text(t.label).tag(t)
                    }
                }
                TextField("例如：example.com", text: $newValue)
                    .autocapitalization(.none)
                Button("添加") { addRule() }
            }
            Section("已启用规则") {
                ForEach($rules) { $rule in
                    Toggle(isOn: $rule.enabled) {
                        VStack(alignment: .leading) {
                            Text(rule.value)
                            Text(rule.type.label).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                .onDelete(perform: deleteRules)
            }
            if let error {
                Section { Text(error).foregroundStyle(.red).font(.footnote) }
            }
        }
        .navigationTitle("规则直连")
        .onChange(of: rules) { _, newRules in
            DirectBypassRuleStore.save(newRules)
        }
    }

    private func addRule() {
        let trimmed = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            error = "规则内容不能为空"
            return
        }
        rules.append(DirectBypassRule(id: UUID(), type: newType, value: trimmed.lowercased(), enabled: true))
        newValue = ""
        error = nil
        DirectBypassRuleStore.save(rules)
    }

    private func deleteRules(at offsets: IndexSet) {
        rules.remove(atOffsets: offsets)
        DirectBypassRuleStore.save(rules)
    }
}
