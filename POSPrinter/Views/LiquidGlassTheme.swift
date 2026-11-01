import SwiftUI

// MARK: - Ambient Liquid Glass Background
public struct LiquidGlassBackground: View {
    public init() {}

    public var body: some View {
        ZStack {
            // Deep sleek backdrop
            Color(red: 0.06, green: 0.08, blue: 0.14)
                .ignoresSafeArea()

            // Dynamic Ambient Liquid Glow Orbs
            GeometryReader { geo in
                Circle()
                    .fill(
                        RadialGradient(
                            gradient: Gradient(colors: [Color(red: 0.2, green: 0.45, blue: 0.95).opacity(0.25), Color.clear]),
                            center: .center,
                            startRadius: 10,
                            endRadius: 180
                        )
                    )
                    .frame(width: 360, height: 360)
                    .blur(radius: 60)
                    .offset(x: -80, y: -80)

                Circle()
                    .fill(
                        RadialGradient(
                            gradient: Gradient(colors: [Color(red: 0.0, green: 0.8, blue: 0.9).opacity(0.18), Color.clear]),
                            center: .center,
                            startRadius: 10,
                            endRadius: 150
                        )
                    )
                    .frame(width: 300, height: 300)
                    .blur(radius: 50)
                    .offset(x: geo.size.width - 180, y: geo.size.height * 0.3)

                Circle()
                    .fill(
                        RadialGradient(
                            gradient: Gradient(colors: [Color(red: 0.6, green: 0.2, blue: 0.9).opacity(0.16), Color.clear]),
                            center: .center,
                            startRadius: 10,
                            endRadius: 160
                        )
                    )
                    .frame(width: 320, height: 320)
                    .blur(radius: 65)
                    .offset(x: 20, y: geo.size.height * 0.7)
            }
            .ignoresSafeArea()
        }
    }
}

// MARK: - ViewModifier: Liquid Glass Card
public struct LiquidGlassCardModifier: ViewModifier {
    public var cornerRadius: CGFloat = 16
    public var isHighlighted: Bool = false

    public func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(.ultraThinMaterial)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(isHighlighted ? 0.5 : 0.28),
                                Color.white.opacity(0.10),
                                Color.white.opacity(0.04),
                                Color.blue.opacity(isHighlighted ? 0.35 : 0.12)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: isHighlighted ? 1.5 : 1.0
                    )
            )
            .shadow(color: Color.black.opacity(0.28), radius: 10, x: 0, y: 5)
    }
}

// MARK: - View Extension Helpers
extension View {
    public func liquidGlassCard(cornerRadius: CGFloat = 16, isHighlighted: Bool = false) -> some View {
        self.modifier(LiquidGlassCardModifier(cornerRadius: cornerRadius, isHighlighted: isHighlighted))
    }
}

// MARK: - Liquid Glass Primary Button
public struct LiquidGlassButton: View {
    public var title: String
    public var icon: String? = nil
    public var tintColor: Color = Color(red: 0.15, green: 0.45, blue: 0.95)
    public var isEnabled: Bool = true
    public var action: () -> Void

    public init(title: String, icon: String? = nil, tintColor: Color = Color(red: 0.15, green: 0.45, blue: 0.95), isEnabled: Bool = true, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.tintColor = tintColor
        self.isEnabled = isEnabled
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let ic = icon {
                    Image(systemName: ic)
                        .font(.system(size: 16, weight: .bold))
                }
                Text(title)
                    .font(.system(size: 15, weight: .bold))
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(
                        isEnabled
                            ? LinearGradient(colors: [tintColor.opacity(0.95), tintColor.opacity(0.75)], startPoint: .topLeading, endPoint: .bottomTrailing)
                            : LinearGradient(colors: [Color.gray.opacity(0.4), Color.gray.opacity(0.25)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(
                        LinearGradient(
                            colors: [Color.white.opacity(isEnabled ? 0.45 : 0.15), Color.white.opacity(0.08)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
            )
            .shadow(color: isEnabled ? tintColor.opacity(0.35) : Color.clear, radius: 8, x: 0, y: 4)
        }
        .disabled(!isEnabled)
    }
}

