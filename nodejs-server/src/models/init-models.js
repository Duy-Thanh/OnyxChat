const { DataTypes } = require('sequelize');

module.exports = (sequelize) => {
  // User model
  const User = sequelize.define('User', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    username: {
      type: DataTypes.STRING,
      allowNull: false,
      unique: true
    },
    email: {
      type: DataTypes.STRING,
      allowNull: false,
      unique: true
    },
    passwordHash: {
      type: DataTypes.STRING,
      allowNull: false
    },
    displayName: {
      type: DataTypes.STRING,
      allowNull: false
    },
    isActive: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    lastActiveAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'users',
    timestamps: true,
    hooks: {
      beforeUpdate: (user) => {
        user.updatedAt = new Date();
      }
    }
  });

  // Add instance methods
  User.prototype.toProfile = function() {
    return {
      id: this.id,
      username: this.username,
      displayName: this.displayName,
      isActive: this.isActive,
      lastActiveAt: this.lastActiveAt
    };
  };

  // Message model
  const Message = sequelize.define('Message', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    senderId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    recipientId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    content: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    contentType: {
      type: DataTypes.STRING,
      defaultValue: 'text'
    },
    encrypted: {
      type: DataTypes.BOOLEAN,
      defaultValue: true
    },
    sent: {
      type: DataTypes.BOOLEAN,
      defaultValue: true
    },
    received: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    read: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    deleted: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    receivedAt: {
      type: DataTypes.DATE,
      allowNull: true
    },
    readAt: {
      type: DataTypes.DATE,
      allowNull: true
    },
    deletedAt: {
      type: DataTypes.DATE,
      allowNull: true
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'messages',
    timestamps: true
  });

  // RefreshToken model
  const RefreshToken = sequelize.define('RefreshToken', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    token: {
      type: DataTypes.STRING,
      allowNull: false,
      unique: true
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    expiresAt: {
      type: DataTypes.DATE,
      allowNull: false
    },
    revoked: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    revokedAt: {
      type: DataTypes.DATE,
      allowNull: true
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'refresh_tokens',
    timestamps: true
  });

  // Define associations
  User.hasMany(Message, { foreignKey: 'senderId', as: 'sentMessages' });
  User.hasMany(Message, { foreignKey: 'recipientId', as: 'receivedMessages' });
  Message.belongsTo(User, { foreignKey: 'senderId', as: 'sender' });
  Message.belongsTo(User, { foreignKey: 'recipientId', as: 'recipient' });
  User.hasMany(RefreshToken, { foreignKey: 'userId' });
  RefreshToken.belongsTo(User, { foreignKey: 'userId' });

  return {
    User,
    Message,
    RefreshToken
  };
}; 